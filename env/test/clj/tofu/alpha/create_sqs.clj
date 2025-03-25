(ns tofu.alpha.create-sqs)

(defn invoke [opts]
  (let [{:keys [name]} opts]
    {:resource {:aws_sqs_queue {(keyword name) {:name name}}}}))
