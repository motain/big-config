(ns big-config.tofu
  (:require
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.call :as call]
   [big-config.core :refer [->workflow choice]]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.run :as run :refer [generic-cmd]]
   [big-config.step-fns :refer [exit-step-fn tap-step-fn]]
   [big-config.unlock :as unlock]
   [bling.core :refer [bling]]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [selmer.parser :refer [<<]]))

(defn ok [opts]
  (merge opts {::bc/exit 0
               ::bc/err nil}))

#_{:clj-kondo/ignore [:unused-binding]}
(defn print-step-fn [f step {:keys [::action
                                    ::aero/module
                                    ::aero/profile
                                    ::lock/owner
                                    ::run/dir
                                    ::run/cmds
                                    ::call/fns
                                    ::bc/err
                                    ::bc/exit] :as opts}]
  (let [[lock-start-step] (lock/lock)
        [unlock-start-step] (unlock/unlock-any)
        [check-start-step] (git/check)
        [prefix color] (if (= exit 0)
                         ["\ueabc" :green.bold]
                         ["\uf05c" :red.bold])
        fn-desc (:desc (first fns))
        msg (cond
              (= step ::read-module) (<< "Action {{ action }} | Module {{ module }} | Profile {{ profile }}")
              (= step ::mkdir) (<< "Making dir {{ dir }}")
              (= step lock-start-step) (<< "Lock (owner {{ owner }})")
              (= step unlock-start-step) (<< "Unlock any")
              (= step check-start-step) (<< "Checking if the working directory is clean {{ check-start-step }}")
              (= step ::compile-tf) (<< "Compiling {{ dir }}/main.tf.json")
              (= step ::run/run-cmd) (<< "Running:\n> {{ cmds | first }}")
              (= step ::call/call-fn) (str "Calling fn: {{ fn-desc }}")
              (= step ::push) (<< "Pushing last commit")
              (and (= step ::end)
                   (> exit 0)
                   (string? err)
                   (not (str/blank? err))) (<< "{{ err }}")
              :else nil)]
    (when msg
      (binding [*out* *err*]
        (println (bling [color (<< (str "{{ prefix }} " msg))])))))
  (let [{:keys [::bc/err
                ::bc/exit] :as opts} (f step opts)
        [_ check-end-step] (git/check)
        prefix "\uf05c"
        msg (cond
              (= step check-end-step) (<< "Working directory is NOT clean")
              (= step ::run/run-cmd) (<< "Failed running:\n> {{ cmds | first }}")
              :else nil)]
    (when (and msg (> exit 0))
      (binding [*out* *err*]
        (println (bling [:red.bold (<< (str "{{ prefix }} " msg))]))))
    opts))

(defn action->opts [{:keys [::action] :as opts}]
  (-> (case action
        (:opts :lock :unlock-any) opts
        (:init :plan :apply :destroy) (merge opts {::run/cmds [(format "tofu %s" (name action))]})
        :ci (merge opts {::run/cmds ["tofu init" "tofu apply -auto-approve" "tofu destroy -auto-approve"]}))
      ok))

(defn mkdir [{:keys [::run/dir] :as opts}]
  (generic-cmd opts (format "mkdir -p %s" dir)))

(defn git-push [opts]
  (generic-cmd opts "git push"))

(defn run-action [step-fns {:keys [::action] :as opts}]
  (let [wf (->workflow {:first-step ::check
                        :last-step ::action-end
                        :wire-fn (fn [step step-fns]
                                   (case step
                                     ::check [(partial git/check step-fns) ::lock]
                                     ::lock [(partial lock/lock step-fns) ::run-cmds]
                                     ::run-cmds [(partial run/run-cmds step-fns) ::push]
                                     ::push [(partial git-push step-fns) ::unlock]
                                     ::unlock [(partial unlock/unlock-any step-fns) ::action-end]
                                     ::action-end [identity]))
                        :next-fn (fn [step next-step opts]
                                   (cond
                                     (= step ::action-end) [nil opts]
                                     (and (= step ::run-cmds)
                                          (= action :ci)) [::unlock opts]
                                     :else (choice {:on-success next-step
                                                    :on-failure ::action-end
                                                    :opts opts})))})]
    (case action
      :opts (do (pp/pprint (into (sorted-map) opts))
                (ok opts))
      :lock (lock/lock step-fns opts)
      :unlock-any (unlock/unlock-any step-fns opts)
      (:init :plan) (run/run-cmds step-fns opts)
      (:apply :destroy :ci) (wf step-fns opts))))

#_{:clj-kondo/ignore [:unused-binding]}
(defn block-destroy-prod-step-fn [start-step f step {:keys [::action ::aero/module ::aero/profile] :as opts}]
  (if (and (= step start-step)
           (#{:destroy :ci} action)
           (#{:prod :production} profile))
    (throw (ex-info (<< "You cannot destroy the module {{ module }} in {{ profile }}") opts))
    (f step opts)))

(defn ^:export main [{[action module profile] :args
                      step-fns :step-fns
                      config :config
                      env :env}]
  (let [action action
        module module
        profile profile
        step-fns (or step-fns [print-step-fn
                               (partial block-destroy-prod-step-fn ::start)
                               (partial exit-step-fn ::end)])
        env (or env :shell)
        wf (->workflow {:first-step ::start
                        :wire-fn (fn [step step-fns]
                                   (case step
                                     ::start [action->opts ::read-module]
                                     ::read-module [aero/read-module ::mkdir]
                                     ::mkdir [mkdir ::call-fns]
                                     ::call-fns [(partial call/call-fns step-fns) ::run-action]
                                     ::run-action [(partial run-action step-fns) ::end]
                                     ::end [identity]))
                        :next-fn (fn [step next-step {:keys [::action] :as opts}]
                                   (cond
                                     (= step ::end) [nil opts]
                                     (and (= step ::read-module)
                                          (#{:opts :lock :unlock-any} action)) [::run-action opts]
                                     :else (choice {:on-success next-step
                                                    :on-failure ::end
                                                    :opts opts})))})]
    (->> (wf step-fns {::action action
                       ::bc/env (or env :shell)
                       ::aero/config config
                       ::aero/module module
                       ::aero/profile profile})
         (into (sorted-map)))))

(comment
  (require '[user :refer [debug-atom]])
  (main {:args [:plan :alpha :dev]
         :step-fns [tap-step-fn
                    print-step-fn
                    (partial block-destroy-prod-step-fn ::start)]
         :env :repl})

  (reset! debug-atom [])
  (-> debug-atom))
