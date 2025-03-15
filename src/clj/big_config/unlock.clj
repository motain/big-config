(ns big-config.unlock
  (:require
   [big-config.lock :refer [check-remote-tag delete-remote-tag delete-tag
                            generate-lock-id]]
   [big-config.utils :refer [default-step-fn]]))

(defn unlock-any
  ([opts]
   (unlock-any default-step-fn opts))
  ([step-fn opts]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step ::generate-lock-id
          opts opts]
     (let [[f next-step] (case step
                           ::generate-lock-id [generate-lock-id ::delete-tag]
                           ::delete-tag [delete-tag ::delete-remote-tag]
                           ::delete-remote-tag [delete-remote-tag ::check-remote-tag]
                           ::check-remote-tag [check-remote-tag ::end]
                           ::end [identity nil])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (if next-step
           (recur next-step $)
           $))))))
