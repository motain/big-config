(ns big-config.git
  (:require
   [big-config :as bc]
   [big-config.utils :refer [->workflow generic-cmd]]))

(defn get-revision [revision key opts]
  (let [revision (cond
                   (string? revision) revision
                   (keyword? revision) (revision opts)
                   :else (throw (ex-info "Revision is neither a string nor a keyword" {:revision revision
                                                                                       :key key
                                                                                       :opts opts})))
        cmd (format "git rev-parse %s" revision)]
    (generic-cmd opts cmd key)))

(defn fetch-origin [opts]
  (generic-cmd opts "git fetch origin"))

(defn upstream-name [key opts]
  (let [cmd "git rev-parse --abbrev-ref @{upstream}"]
    (generic-cmd opts cmd key)))

(defn git-diff [opts]
  (generic-cmd opts "git diff --quiet"))

(defn compare-revisions [opts]
  (let [{:keys [::prev-revision
                ::current-revision
                ::origin-revision]} opts
        res (or (= prev-revision origin-revision)
                (= current-revision origin-revision))]
    (merge opts (if res
                  {::bc/exit 0
                   ::bc/err nil}
                  {::bc/exit 1
                   ::bc/err "The local revisions don't match the remote revision"}))))

(def check (->workflow {:first-step ::git-diff
                        :wire-fn (fn [step _]
                                   (case step
                                     ::git-diff [git-diff ::fetch-origin]
                                     ::fetch-origin [fetch-origin ::upstream-name]
                                     ::upstream-name [(partial upstream-name ::upstream-name) ::pre-revision]
                                     ::pre-revision [(partial get-revision "HEAD~1" ::prev-revision) ::current-revision]
                                     ::current-revision [(partial get-revision "HEAD" ::current-revision) ::origin-revision]
                                     ::origin-revision [(partial get-revision ::upstream-name ::origin-revision) ::compare-revisions]
                                     ::compare-revisions [compare-revisions ::end]
                                     ::end [identity]))
                        :next-fn ::end}))

(comment
  (->> (check {})
       (into (sorted-map))))
