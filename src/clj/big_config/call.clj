(ns big-config.call
  (:require
   [babashka.fs :as fs]
   [big-config :as bc]
   [big-config.core :refer [->workflow]]
   [cheshire.core :as json]))

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
               :wire-fn (fn [step _]
                          (case step
                            ::call-fn [call-fn ::call-fn]
                            ::end [identity]))
               :next-fn (fn [step _ {:keys [::bc/exit ::fns] :as opts}]
                          (cond
                            (and (seq (rest fns))
                                 (= exit 0)) [::call-fn (merge opts {::fns (rest fns)})]
                            (= step ::end) [nil opts]
                            :else [::end opts]))}))

(defn ^:export mkdir-and-spit [{:keys [out type f args]}]
  (-> (fs/parent out)
      (fs/create-dirs))
  (let [res (-> (symbol f)
                requiring-resolve
                (apply args))
        res (case type
              :text res
              :json (json/generate-string res {:pretty true}))]
    (spit out res)))

(comment
  (call-fns [(fn [f step opts]
               (println step)
               (f step opts))]
            {::fns [{:f "big-config.call/mkdir-and-spit"
                     :desc "spit main.tf.json"
                     :args [{:out "big-infra/tofu/251213589273/alpha/main.tf.json"
                             :type :json
                             :f "tofu.alpha.main/invoke"
                             :args [{:aws-account-id "251213589273"
                                     :region "eu-west-1"
                                     :module :alpha}]}]}
                    {:f "big-config.call/mkdir-and-spit"
                     :desc "spit user_data.sh"
                     :args [{:out "big-infra/tofu/251213589273/alpha/files/user_data.sh"
                             :type :text
                             :f "tofu.alpha.user-data/invoke"
                             :args []}]}]}))
