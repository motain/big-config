(ns big-config.git
  (:require
   [big-config.utils :refer [exit-with-code? generic-cmd recur-with-no-error]]))

(defn get-revision [opts revision key]
  (let [cmd (format "git rev-parse %s" revision)]
    (generic-cmd opts cmd key)))

(defn fetch-origin [opts]
  (generic-cmd opts "git fetch origin"))

(defn upstream-name [opts key]
  (let [cmd "git rev-parse --abbrev-ref @{upstream}"]
    (generic-cmd opts cmd key)))

(defn git-diff [opts]
  (generic-cmd opts "git diff --quiet"))

(defn ^:export check [_]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :git-diff
         opts {}]
    (let [opts (update opts :steps (fnil conj []) step)]
      (case step
        :git-diff (as-> (git-diff opts) $
                    (recur-with-no-error :fetch-origin $ "The repo is dirty"))
        :fetch-origin (as-> (fetch-origin opts) $
                        (recur-with-no-error :upstream-name $))
        :upstream-name (as-> (upstream-name opts :upstream-name) $
                         (recur-with-no-error :prev-revision $))
        :prev-revision (as-> (get-revision opts "HEAD~1" :prev-revision) $
                         (recur-with-no-error :origin-revision $))
        :origin-revision (as-> (:upstream-name opts) $
                           (get-revision opts $ :origin-revision)
                           (recur-with-no-error :compare-revisions $))
        :compare-revisions (let [{:keys [prev-revision origin-revision]} opts]
                             (if (= prev-revision origin-revision)
                               (exit-with-code? 0 opts)
                               (exit-with-code? 1 opts)))))))
