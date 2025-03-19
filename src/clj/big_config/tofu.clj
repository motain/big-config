(ns big-config.tofu
  (:require
   [clojure.string :as str]
   [big-config.lock :as lock-b :refer [lock]]
   [big-config.unlock :refer [unlock-any]]
   [big-config.utils :refer [choice default-step-fn run-cmd]]))

(defn tofu-cmd [cmd opts]
  (let [parent-run-cmd (:big-config.run/run-cmd opts)
        parent-cmd (:big-config.run/cmd opts)
        overriden-run-cmd (str/replace parent-run-cmd parent-cmd cmd)
        overriden-opts (assoc opts :big-config.run/run-cmd overriden-run-cmd)
        new-opts (run-cmd overriden-opts)
        new-opts (assoc new-opts :big-config.run/run-cmd parent-run-cmd)]
    new-opts))

(defn run-ci
  ([opts]
   (run-ci default-step-fn opts))
  ([step-fn opts]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step ::lock-acquire
          opts opts]
     (let [[f next-step errmsg] (case step
                                  ::lock-acquire [(partial lock step-fn) ::tofu-init "Failed to acquire the lock"]
                                  ::tofu-init [(partial tofu-cmd "init") ::tofu-apply]
                                  ::tofu-apply [(partial tofu-cmd "apply") ::tofu-destroy]
                                  ::tofu-destroy [(partial tofu-cmd "destroy") ::lock-release-any-owner]
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

(comment (run-ci {:foo "foo"
                  :bar "bar"
                  ::lock-b/owner "developer"
                  ::lock-b/lock-keys [:foo :bar]}))
