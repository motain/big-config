(ns big-config.tofu
  (:require
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.call :as call]
   [big-config.core :refer [->step-fn ->workflow choice ok]]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.run :as run :refer [generic-cmd]]
   [big-config.step-fns :refer [->exit-step-fn tap-step-fn]]
   [big-config.unlock :as unlock]
   [bling.core :refer [bling]]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [selmer.parser :as p]
   [selmer.util :as util]))

(def print-step-fn
  (->step-fn {:before-f (fn [step {:keys [::bc/err
                                          ::bc/exit] :as opts}]
                          (binding [util/*escape-variables* false]
                            (let [[lock-start-step] (lock/lock)
                                  [unlock-start-step] (unlock/unlock-any)
                                  [check-start-step] (git/check)
                                  [prefix color] (if (= exit 0)
                                                   ["\ueabc" :green.bold]
                                                   ["\uf05c" :red.bold])
                                  msg (cond
                                        (= step ::read-module) (p/render "Action {{ big-config..tofu/action|default:nil }} | Module {{ big-config..aero/module|default:nil }} | Profile {{ big-config..aero/profile|default:nil }} | Config {{ big-config..tofu/config|default:nil }}" opts)
                                        (= step ::mkdir) (p/render "Making dir {{ big-config..run/dir }}" opts)
                                        (= step lock-start-step) (p/render "Lock (owner {{ big-config..lock/owner }})" opts)
                                        (= step unlock-start-step) "Unlock any"
                                        (= step check-start-step) "Checking if the working directory is clean"
                                        (= step ::compile-tf) (p/render "Compiling {{ big-config..run/dir }}/main.tf.json" opts)
                                        (= step ::run/run-cmd) (p/render "Running:\n> {{ big-config..run/cmds | first}}" opts)
                                        (= step ::call/call-fn) (p/render "Calling fn: {{ desc }}" (first (::call/fns opts)))
                                        (= step ::push) "Pushing last commit"
                                        (and (= step ::end)
                                             (> exit 0)
                                             (string? err)
                                             (not (str/blank? err))) err
                                        :else nil)]
                              (when msg
                                (binding [*out* *err*]
                                  (println (bling [color (p/render (str "{{ prefix }} " msg) {:prefix prefix})])))))))
              :after-f (fn [step {:keys [::bc/exit] :as opts}]
                         (let [[_ check-end-step] (git/check)
                               prefix "\uf05c"
                               msg (cond
                                     (= step check-end-step) "Working directory is NOT clean"
                                     (= step ::run/run-cmd) (p/render "Failed running:\n> {{ big-config..run/cmds | first }}" opts)
                                     :else nil)]
                           (when (and msg
                                      (> exit 0))
                             (binding [*out* *err*]
                               (println (bling [:red.bold (p/render (str "{{ prefix }} " msg) {:prefix prefix})]))))))}))

(defn mkdir [{:keys [::run/dir] :as opts}]
  (generic-cmd opts (format "mkdir -p %s" dir)))

(defn git-push [opts]
  (generic-cmd opts "git push"))

(def lock-action
  (fn [action step-fns opts]
    (let [wf (->workflow {:first-step ::check
                          :last-step ::action-end
                          :wire-fn (fn [step step-fns]
                                     (case step
                                       ::check [(partial git/check step-fns) ::lock]
                                       ::lock [(partial lock/lock step-fns) ::run-cmds]
                                       ::run-cmds [(partial run/run-cmds step-fns) ::push]
                                       ::push [git-push ::unlock]
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
      (wf step-fns opts))))

(defn run-action [step-fns {:keys [::action] :as opts}]
  (let [opts (assoc opts ::run/cmds (case action
                                      :clean ["rm -rf .terraform"]
                                      (:opts :lock :unlock-any) []
                                      (:init :plan :apply :destroy) [(format "tofu %s" (name action))]
                                      :ci ["tofu init" "tofu apply -auto-approve" "tofu destroy -auto-approve"]))]
    (case action
      :opts (do (pp/pprint (into (sorted-map) opts))
                (ok opts))
      :clean (run/run-cmds step-fns opts)
      :lock (lock/lock step-fns opts)
      :unlock-any (unlock/unlock-any step-fns opts)
      (:init :plan) (run/run-cmds step-fns opts)
      (:apply :destroy :ci) (lock-action action step-fns opts))))

(defn block-destroy-prod-step-fn [start-step]
  (->step-fn {:before-f (fn [step {:keys [::action ::aero/profile] :as opts}]
                          (let [msg (p/render "You cannot destroy module {{ big-config..aero/module }} in {{ big-config..aero/profile }}" opts)]
                            (when (and (= step start-step)
                                       (#{:destroy :ci} action)
                                       (#{:prod :production} profile))
                              (throw (ex-info msg opts)))))}))

(defn ^:export main [{[action module profile] :args
                      step-fns :step-fns
                      config :config
                      env :env}]
  (let [action action
        module module
        profile profile
        step-fns (or step-fns [print-step-fn
                               (block-destroy-prod-step-fn ::start)
                               (->exit-step-fn ::end)])
        env (or env :shell)
        wf (->workflow {:first-step ::start
                        :wire-fn (fn [step step-fns]
                                   (case step
                                     ::start [#(ok %) ::read-module]
                                     ::read-module [aero/read-module ::mkdir]
                                     ::mkdir [mkdir ::call-fns]
                                     ::call-fns [(partial call/call-fns step-fns) ::run-action]
                                     ::run-action [(partial run-action step-fns) ::end]
                                     ::end [identity]))
                        :next-fn (fn [step next-step {:keys [::action] :as opts}]
                                   (cond
                                     (= step ::end) [nil opts]
                                     (and (= action :clean)
                                          (= step ::mkdir))  [::run-action opts]
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
  (main {:args [:clean :alpha :dev]
         :config "big-infra/big-config.edn"
         :step-fns [tap-step-fn
                    print-step-fn
                    (block-destroy-prod-step-fn ::start)
                    (->exit-step-fn ::end)]
         :env :repl})

  (reset! debug-atom [])
  (-> debug-atom))
