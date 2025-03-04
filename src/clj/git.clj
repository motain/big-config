(ns git
  (:require
   [babashka.process :as process]
   [utils :refer [exit-with-code? recur-with-no-error]]))

(defn get-revision [opts revision key]
  (let [res (-> (process/shell {:continue true
                                :out :string
                                :err :string}
                               (format "git rev-parse %s" revision)))]

    (-> opts
        (assoc key (:out res))
        (update :cmd-results (fnil conj []) res))))

(defn fetch-origin [opts]
  (let [res (-> (process/shell {:continue true
                                :out :string
                                :err :string}
                               "git fetch origin"))]
    (update opts :cmd-results (fnil conj []) res)))

(defn git-diff [opts]
  (let [res (-> (process/shell {:continue true
                                :out :string
                                :err :string}
                               "git diff --quiet"))]
    (update opts :cmd-results (fnil conj []) res)))

(defn ^:export check [_]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :git-diff
         opts {}]
    (let [opts (update opts :steps (fnil conj []) step)]
      (case step
        :git-diff (as-> (git-diff opts) $
                    (recur-with-no-error :fetch-origin $ "The repo is dirty"))
        :fetch-origin (as-> (fetch-origin opts) $
                        (recur-with-no-error :prev-revision $))
        :prev-revision (as-> (get-revision opts "HEAD~1" :prev-revision) $
                         (recur-with-no-error :origin-revision $))
        :origin-revision (as-> (get-revision opts "origin/main" :origin-revision) $
                           (recur-with-no-error :compare-revisions $))
        :compare-revisions (let [{:keys [prev-revision origin-revision]} opts]
                             (if (= prev-revision origin-revision)
                               (exit-with-code? 0 opts)
                               (exit-with-code? 1 opts)))))))
