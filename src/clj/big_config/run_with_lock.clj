(ns big-config.run-with-lock
  (:require
   [big-config.git :refer [check]]
   [big-config.lock :refer [lock]]
   [big-config.run :refer [generate-main-tf-json]]
   [big-config.unlock :refer [unlock-any]]
   [big-config.utils :refer [choice default-step-fn git-push run-cmd]]))

(defn run-with-lock
  ([opts]
   (run-with-lock default-step-fn opts))
  ([step-fn opts]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step ::lock-acquire
          opts opts]
     (let [[f next-step errmsg] (case step
                                  ::lock-acquire [(partial lock step-fn) ::git-check "Failed to acquire the lock"]
                                  ::git-check [(partial check step-fn) ::generate-main-tf-json "The working directory is not clean"]
                                  ::generate-main-tf-json [generate-main-tf-json ::run-cmd]
                                  ::run-cmd [run-cmd ::git-push "The command executed with the lock failed"]
                                  ::git-push [git-push ::lock-release-any-owner]
                                  ::lock-release-any-owner [(partial unlock-any step-fn) ::end "Failed to release the lock"]
                                  ::end [identity])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (if next-step
           (choice {:on-success next-step
                    :on-failure ::end
                    :errmsg errmsg
                    :opts $})
           $))))))

(comment)
