(ns lein-monolith.plugin
  "This namespace runs inside of Leiningen on all projects and will
  automatically activate the `with-all` task if the project map sets a truthy
  value for the `:monolith` key."
  (:require
    [clojure.java.io :as jio]
    (leiningen.core
      [main :as lein]
      [project :as project])
    [lein-monolith.config :as config]))


(defn- dedupe-dependencies
  "Given a vector of dependency coordinates, deduplicate and ensure there are no
  conflicting versions found."
  [dependencies]
  (reduce-kv
    (fn [current dep-name specs]
      (let [specs (distinct specs)]
        (if (= 1 (count specs))
          ; Only one (unique) version declared, add to current vector.
          (conj current (first specs))
          ; Multiple versions or specs declared! Try to resolve.
          (let [versions (distinct (map second specs))
                projects (map (comp :monolith/project meta) specs)]
            (if (= 1 (count versions))
              ; Only one version in use, so the distinction must be something
              ; like an :exclude directive in the spec. Use the first one and
              ; warn about the conflict.
              (let [choice (first specs)]
                (lein/warn "WARN: Multiple dependency specs found for" dep-name
                           "in projects" projects "- using" (pr-str choice)
                           "from" (:monolith/project (meta choice)))
                (conj current choice))
              ; Multiple versions found, set the error flag and continue.
              (lein/abort "ERROR: Multiple dependency versions found for"
                          dep-name "in projects" projects ":" versions))))))
    []
    (group-by first dependencies)))


(defn monolith-profile
  "Constructs a profile map containing merged source and test paths."
  [subprojects]
  (->
    (reduce-kv
      (fn [profile project-name project]
        (let [dependencies (map #(vary-meta % assoc :monolith/project project-name)
                                (:dependencies project))]
          (-> profile
              (update :source-paths concat (:source-paths project))
              (update :test-paths   concat (:test-paths project))
              (update :dependencies concat dependencies))))
      {:source-paths []
       :test-paths []
       :dependencies []}
      subprojects)
    ; TODO: check dependency versions?
    (update :dependencies dedupe-dependencies)
    (assoc :monolith/subprojects subprojects)))


(defn add-profile
  "Adds the monolith profile to the given project if it's not already present."
  [project]
  (if (get-in project [:profiles :monolith/all])
    project
    (let [config (config/read!)
          subprojects (config/load-subprojects! config)
          profile (monolith-profile subprojects)]
      (lein/debug "Adding monolith profile to project...")
      (project/add-profiles project {:monolith/all profile}))))


(defn activate-profile
  "Activates the monolith profile in the project if it's not already active."
  [project]
  (if (contains? (set (:active-profiles (meta project))) :monolith/all)
    project
    (do (lein/debug "Merging monolith profile into project...")
        (project/merge-profiles project [:monolith/all]))))


(defn middleware
  "Automatically adds the merged monolith profile to the project if it contains
  a truthy value in `:monolith`."
  [project]
  (if (:monolith project)
    ; Monolith project, load up merged profile.
    (-> project
        (add-profile)
        (activate-profile))
    ; Normal project, don't activate.
    project))
