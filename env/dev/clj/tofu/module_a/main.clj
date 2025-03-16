(ns tofu.module-a.main
  (:require
   [big-config.lock :as lock]
   [big-config.utils :as bc]
   [clojure.string :as str]
   [tofu.common.create-provider :as create-provider]
   [tofu.module-a.create-sqs :as create-sqs]))

(defn ^:export invoke [opts]
  {:post [(as-> % $
            (get-in $ [:resource :aws_sqs_queue])
            (count $)
            (= 2 $))]}
  (let [{:keys [::lock/aws-account-id
                ::lock/region]} opts
        bucket (str/join "-" (vector "tf-state" aws-account-id region))
        queues (for [n (range 2)]
                 (create-sqs/invoke {:name (str "sqs-" n)}))

        provider (case aws-account-id
                   "251213589273"  (-> opts
                                       (assoc ::lock/bucket bucket)
                                       create-provider/invoke))]
    (->> [provider]
         (concat queues)
         (apply bc/deep-merge)
         bc/nested-sort-map)))

(comment
  (-> {:aws-account-id "251213589273"
       :region "eu-west-1"}
      invoke))
