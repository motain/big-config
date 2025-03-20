(ns big-config.unlock
  (:require
   [big-config.lock :refer [check-remote-tag delete-remote-tag delete-tag
                            generate-lock-id]]
   [big-config.utils :refer [->workflow]]))

(def unlock-any (->workflow {:first-step ::generate-lock-id
                             :wire-fn (fn [step _]
                                        (case step
                                          ::generate-lock-id [generate-lock-id ::delete-tag]
                                          ::delete-tag [delete-tag ::delete-remote-tag]
                                          ::delete-remote-tag [delete-remote-tag ::check-remote-tag]
                                          ::check-remote-tag [check-remote-tag ::end]
                                          ::end [identity]))
                             :next-fn (fn [_step next-step opts]
                                        (if next-step
                                          [next-step opts]
                                          [nil opts]))}))

(comment
  (->> (unlock-any {})
       (into (sorted-map))))
