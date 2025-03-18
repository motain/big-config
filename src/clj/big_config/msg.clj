(ns big-config.msg
  (:require
   [bling.core :refer [bling]]))

(defn step->message
  "no require to avoid circular dependencies"
  [step]
  (let [messages {:big-config.aero/read-module "Reading the module"
                  :big-config.run/generate-main-tf-json "Generating the main.tf.json file"
                  :big-config.run/run-cmd "Running the tofu command"
                  :big-config.run-with-lock/lock-acquire "Acquiring lock"
                  :big-config.run-with-lock/git-check "Checking if there are files not in the index"
                  :big-config.run-with-lock/generate-main-tf-json "Generating the main.tf.json file"
                  :big-config.run-with-lock/run-cmd "Running the tofu command"
                  :big-config.run-with-lock/git-push "Pushing the changes to git"
                  :big-config.run-with-lock/lock-release-any-owner "Releasing lock"}
        msg (step messages)]
    (when msg
      (bling [:green.bold msg]))))
