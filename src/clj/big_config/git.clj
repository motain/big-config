(ns big-config.git
  (:require
   [big-config.utils :refer [generic-cmd-v2 recur-ok-or-end
                             error-for-step]]))

(defn get-revision [opts revision key]
  (let [cmd (format "git rev-parse %s" revision)]
    (generic-cmd-v2 opts cmd key)))

(defn fetch-origin [opts]
  (generic-cmd-v2 opts "git fetch origin"))

(defn upstream-name [opts key]
  (let [cmd "git rev-parse --abbrev-ref @{upstream}"]
    (generic-cmd-v2 opts cmd key)))

(defn git-diff [opts]
  (generic-cmd-v2 opts "git diff --quiet"))

(defn compare-revisions [opts]
  (let [{:keys [prev-revision
                current-revision
                origin-revision]} opts
        res (or (= prev-revision origin-revision)
                (= current-revision origin-revision))]
    (merge opts (if res
                  {:exit 0
                   :err nil}
                  {:exit 1
                   :err "The local revisions don't match the remote revision"}))))

(defn ^:export check
  ([opts]
   (check opts identity))
  ([opts end-fn]
   (loop [step :git-diff
          opts opts]
     (let [opts (update opts :steps (fnil conj []) step)
           error-msg (error-for-step step)]
       (case step
         :git-diff (as-> (git-diff opts) $
                     (recur-ok-or-end :fetch-origin $ error-msg))
         :fetch-origin (as-> (fetch-origin opts) $
                         (recur-ok-or-end :upstream-name $))
         :upstream-name (as-> (upstream-name opts :upstream-name) $
                          (recur-ok-or-end :prev-revision $))
         :prev-revision (as-> (get-revision opts "HEAD~1" :prev-revision) $
                          (recur-ok-or-end :current-revision $))
         :current-revision (as-> (get-revision opts "HEAD" :current-revision) $
                             (recur-ok-or-end :origin-revision $))
         :origin-revision (as-> (:upstream-name opts) $
                            (get-revision opts $ :origin-revision)
                            (recur-ok-or-end :compare-revisions $))
         :compare-revisions (as-> (compare-revisions opts) $
                              (recur :end $))
         :end (end-fn opts))))))

(comment)
