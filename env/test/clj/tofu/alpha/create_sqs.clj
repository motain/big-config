(ns tofu.alpha.create-sqs)

(defn invoke [{:keys [name]}]
  {:resource {:aws_sqs_queue {(keyword name) {:name name}}}})
