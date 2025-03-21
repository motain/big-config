(ns big-config.workflows
  (:require
   [big-config :as bc]
   [big-config.git :refer [check]]
   [big-config.lock :refer [lock]]
   [big-config.protocols :refer [Workflow execute-step handle-exit]]
   [big-config.run :refer [generate-main-tf-json]]
   [big-config.unlock :refer [unlock-any]]
   [big-config.utils :refer [git-push run-cmd]]))

(defn run-workflow-steps [steps start-step opts executor]
  (loop [step-key start-step
         opts opts]
    (let [{:keys [f next error]} (get steps step-key)
          new-opts (execute-step executor {:f f :step step-key :opts opts})]
      (if next
        (if (= (::bc/exit new-opts) 0)
          (recur next new-opts)
          (do
            (when error
              (assoc new-opts ::bc/err error))
            (handle-exit executor new-opts)))
        (handle-exit executor new-opts)))))

(defrecord RunWithLockWorkflow []
  Workflow
  (get-steps [this]
    {::lock-acquire             {:f #(lock %1 %2) 
                                :next ::git-check 
                                :error "Failed to acquire the lock"}
     ::git-check               {:f #(check %1 %2)
                                :next ::generate-main-tf-json
                                :error "The working directory is not clean"}
     ::generate-main-tf-json   {:f generate-main-tf-json
                                :next ::run-cmd}
     ::run-cmd                 {:f run-cmd
                                :next ::git-push
                                :error "The command executed with the lock failed"}
     ::git-push                {:f git-push
                                :next ::lock-release-any-owner}
     ::lock-release-any-owner  {:f #(unlock-any %1 %2)
                                :next ::end
                                :error "Failed to release the lock"}
     ::end                     {:f identity}})
  
  (run-workflow [this executor opts]
    (run-workflow-steps (.get-steps this) ::lock-acquire opts executor)))

(defrecord RunWorkflow []
  Workflow
  (get-steps [this]
    {::generate-main-tf-json   {:f generate-main-tf-json
                                :next ::run-cmd}
     ::run-cmd                 {:f run-cmd
                                :next ::end}
     ::end                     {:f identity}})
  
  (run-workflow [this executor opts]
    (run-workflow-steps (.get-steps this) ::generate-main-tf-json opts executor)))