(ns big-config.call
  (:require
   [big-config :as bc]
   [big-config.core :refer [->workflow]]))

(defn call-fn [{:keys [::fns] :as opts}]
  (let [{:keys [f args]} (first fns)]
    (-> (symbol f)
        requiring-resolve
        (apply args))
    (merge opts {::bc/exit 0
                 ::bc/err nil})))

(def call-fns
  (->workflow {:first-step ::call-fn
               :last-step ::call-fn
               :wire-fn (constantly [call-fn ::call-fn])
               :next-fn (fn [_ _ {:keys [::fns] :as opts}]
                          (if (seq (rest fns))
                            [::call-fn (merge opts {::fns (rest fns)})]
                            [nil opts]))}))

(comment
  (call-fns [(fn [f step opts]
               (println step)
               (f step opts))]
            {::fns [{:f "clojure.core/println"
                     :args [:bar]}
                    {:f "clojure.core/println"
                     :args [:foo]}]}))
