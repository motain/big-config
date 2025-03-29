(ns big-config.step-fns
  (:require
   [big-config :as bc]
   [big-config.core :refer [->step-fn]]))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(def tap-step-fn
  (->step-fn {:before-f (fn [step opts]
                          (tap> [step :before opts]))
              :after-f :same}))

(defn ->exit-step-fn [end]
  (->step-fn {:after-f (fn [step {:keys [::bc/env ::bc/exit]}]
                         (when (and (= step end)
                                    (not= env :repl))
                           (exit-with-code exit)))}))
