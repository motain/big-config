(ns big-config.run-with-lock
  (:require
   [big-config.git :refer [check]]
   [big-config.lock :refer [lock]]
   [big-config.run :refer [generate-main-tf-json]]
   [big-config.unlock :refer [unlock-any]]
   [big-config.utils :refer [->workflow git-push
                             run-cmd]]))

(def run-with-lock (->workflow {:first-step ::lock-acquire
                                :wire-fn (fn [step step-fn]
                                           (case step
                                             ::lock-acquire [(partial lock step-fn) ::git-check "Failed to acquire the lock"]
                                             ::git-check [(partial check step-fn) ::generate-main-tf-json "The working directory is not clean"]
                                             ::generate-main-tf-json [generate-main-tf-json ::run-cmd]
                                             ::run-cmd [run-cmd ::git-push "The command executed with the lock failed"]
                                             ::git-push [git-push ::lock-release-any-owner]
                                             ::lock-release-any-owner [(partial unlock-any step-fn) ::end "Failed to release the lock"]
                                             ::end [identity]))
                                :next-fn ::end}))

(comment
  (->> (run-with-lock #:big-config.lock {:aws-account-id "111111111111"
                                         :region "eu-west-1"
                                         :ns "test.module"
                                         :fn "invoke"
                                         :owner "CI"
                                         :lock-keys [:big-config.lock/aws-account-id
                                                     :big-config.lock/region
                                                     :big-config.lock/ns]
                                         :big-config.run/run-cmd "true"
                                         :big-config/test-mode true
                                         :big-config/env :repl})
       (into (sorted-map))))
