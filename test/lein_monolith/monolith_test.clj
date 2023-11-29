(ns lein-monolith.monolith-test
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [deftest is]]
    [lein-monolith.test-utils :refer [use-example-project read-example-project]]
    [leiningen.monolith :as monolith])
  (:import
    java.util.regex.Pattern))


(use-example-project)


(defn- absolute-path
  "Return an absolute java.nio.file.Path for the given file-ish input."
  [x]
  (.. (io/as-file x) toPath toAbsolutePath))


(defn- read-pprint-output
  "Runs lein pprint with the given key path using the :monolith/all profile."
  [& ks]
  (->> (map str ks)
       (apply vector "pprint")
       (monolith/with-all (read-example-project))
       with-out-str
       read-string))


(defn- relativize-path
  "Convert absolute paths to paths relative to the example project."
  [path]
  (str (.relativize (absolute-path "example") (absolute-path path))))


(defn- relativize-pprint-output
  "Read pprint output and convert absolute paths to paths relative to the
  example project."
  [& ks]
  (->> ks
       (apply read-pprint-output)
       (map relativize-path)))


(deftest with-all-test
  (is (= ["apps/app-a/resources"
          "dev-resources"
          "libs/lib-a/resources"
          "libs/lib-b/resources"
          "libs/subdir/lib-c/resources"
          "resources"]
         (relativize-pprint-output :resource-paths)))

  (is (= ["apps/app-a/src"
          "libs/lib-a/src"
          "libs/lib-b/src"
          "libs/subdir/lib-c/src"
          "src"]
         (relativize-pprint-output :source-paths)))

  (is (= ["apps/app-a/test/integration"
          "apps/app-a/test/unit"
          "libs/lib-a/test/integration"
          "libs/lib-a/test/unit"
          "libs/lib-b/test/integration"
          "libs/lib-b/test/unit"
          "libs/subdir/lib-c/test/integration"
          "libs/subdir/lib-c/test/unit"
          "test/integration"
          "test/unit"]
         (relativize-pprint-output :test-paths))))


(deftest classpath-all-test
  (let [all-absolute-paths
        (-> (monolith/classpath-all (read-example-project))
            (with-out-str)
            (str/split (Pattern/compile (str java.io.File/pathSeparatorChar))))

        internal-relative-paths
        (->> all-absolute-paths
             (remove #(str/includes? % "/.m2/"))
             (map relativize-path))]
    (is (= ["apps/app-a/test/integration"
            "apps/app-a/test/unit"
            "libs/lib-a/test/integration"
            "libs/lib-a/test/unit"
            "libs/lib-b/test/integration"
            "libs/lib-b/test/unit"
            "libs/subdir/lib-c/test/integration"
            "libs/subdir/lib-c/test/unit"
            "test/integration"
            "test/unit"
            "apps/app-a/src"
            "libs/lib-a/src"
            "libs/lib-b/src"
            "libs/subdir/lib-c/src"
            "src"
            "apps/app-a/resources"
            "dev-resources"
            "libs/lib-a/resources"
            "libs/lib-b/resources"
            "libs/subdir/lib-c/resources"
            "resources"
            "target/compiled"]
           internal-relative-paths))))
