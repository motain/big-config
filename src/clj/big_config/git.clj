(ns big-config.git
  (:require
   [big-config.utils :refer [exit-with-code generic-cmd recur-ok-or-end
                             error-for-step]]))

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
        :compare-revisions (let [{:keys [prev-revision
                                         current-revision
                                         origin-revision]} opts]
                             (if (or (= prev-revision origin-revision)
                                     (= current-revision origin-revision))
                               opts
                               (exit-with-code 1)))))))
