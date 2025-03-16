(ns big-config.msg
  (:require
   [big-config.run :as run]
   [big-config.run-with-lock :as rwl]
   [bling.core :refer [bling]]))

(defn step->message [step]
  (let [messages {::run/generate-main-tf-json "Generating the main.tf.json file"
                  ::run/run-cmd "Running the tofu command"
                  ::rwl/lock-acquire "Acquiring lock"
                  ::rwl/git-check "Checking if there are files not in the index"
                  ::rwl/generate-main-tf-json "Generating the main.tf.json file"
                  ::rwl/run-cmd "Running the tofu command"
                  ::rwl/git-push "Pushing the changes to git"
                  ::rwl/lock-release-any-owner "Releasing lock"}
        msg (step messages)]
    (when msg
      (bling [:green.bold msg]))))
