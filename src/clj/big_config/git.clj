(ns big-config.git
  (:require
   [big-config.utils :refer [exit-with-code? generic-cmd recur-with-no-error]]
   [big-config.utils :as utils]
   [clojure.pprint :as pp]))

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

(defn ^:export check [opts]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :git-diff
         opts opts]
    (utils/starting-step step)
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (utils/step-failed step)]
      (case step
        :git-diff (as-> (git-diff opts) $
                    (recur-with-no-error :fetch-origin $ error-msg))
        :fetch-origin (as-> (fetch-origin opts) $
                        (recur-with-no-error :upstream-name $))
        :upstream-name (as-> (upstream-name opts :upstream-name) $
                         (recur-with-no-error :prev-revision $))
        :prev-revision (as-> (get-revision opts "HEAD~1" :prev-revision) $
                         (recur-with-no-error :current-revision $))
        :current-revision (as-> (get-revision opts "HEAD" :current-revision) $
                            (recur-with-no-error :origin-revision $))
        :origin-revision (as-> (:upstream-name opts) $
                           (get-revision opts $ :origin-revision)
                           (recur-with-no-error :compare-revisions $))
        :compare-revisions (let [{:keys [prev-revision
                                         current-revision
                                         origin-revision]} opts]
                             (if (or (= prev-revision origin-revision)
                                     (= current-revision origin-revision))
                               opts
                               (exit-with-code? 1 opts)))))))
