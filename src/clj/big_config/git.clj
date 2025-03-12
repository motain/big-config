(ns big-config.git
  (:require
   [big-config.utils :refer [default-step-fn generic-cmd recur-ok-or-end]]))

(defn get-revision [revision key opts]
  (let [cmd (format "git rev-parse %s" revision)]
    (generic-cmd opts cmd key)))

(defn fetch-origin [opts]
  (generic-cmd opts "git fetch origin"))

(defn upstream-name [key opts]
  (let [cmd "git rev-parse --abbrev-ref @{upstream}"]
    (generic-cmd opts cmd key)))

(defn git-diff [opts]
  (generic-cmd opts "git diff --quiet"))

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

(defn check
  ([opts]
   (check opts default-step-fn))
  ([opts step-fn]
   (loop [step :git-diff
          opts opts]
     (case step
       :git-diff (as-> (step-fn {:f git-diff
                                 :step step
                                 :opts opts}) $
                   (recur-ok-or-end :fetch-origin $))
       :fetch-origin (as-> (step-fn {:f fetch-origin
                                     :step step
                                     :opts opts}) $
                       (recur-ok-or-end :upstream-name $))
       :upstream-name (as-> (step-fn {:f (partial upstream-name :upstream-name)
                                      :step step
                                      :opts opts}) $
                        (recur-ok-or-end :prev-revision $))
       :prev-revision (as-> (step-fn {:f (partial get-revision "HEAD~1" :prev-revision)
                                      :step step
                                      :opts opts}) $
                        (recur-ok-or-end :current-revision $))
       :current-revision (as-> (step-fn {:f (partial get-revision "HEAD" :current-revision)
                                         :step step
                                         :opts opts}) $
                           (recur-ok-or-end :origin-revision $))
       :origin-revision (as-> (step-fn {:f (partial get-revision (:upstream-name opts) :origin-revision)
                                        :step step
                                        :opts opts}) $
                          (recur-ok-or-end :compare-revisions $))
       :compare-revisions (as-> (step-fn {:f compare-revisions
                                          :step step
                                          :opts opts}) $
                            (recur :end $))
       :end (step-fn {:f identity
                      :step step
                      :opts opts})))))

(comment)
