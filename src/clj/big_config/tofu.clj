(ns big-config.tofu
  (:require
   [babashka.process :as process]
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.core :refer [->workflow choice]]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.run :as run :refer [generic-cmd handle-cmd]]
   [big-config.unlock :as unlock]
   [bling.core :refer [bling]]
   [cheshire.core :as json]
   [clojure.pprint :as pp]
   [selmer.parser :refer [<<]]))

(defn ok [opts]
  (merge opts {::bc/exit 0
               ::bc/err nil}))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defn exit-step-fn [end f step opts]
  (let [{:keys [::bc/env ::bc/exit] :as opts} (f step opts)]
    (if (= step end)
      (case env
        :shell (exit-with-code exit)
        :repl opts)
      opts)))

(defn trace-step-fn [f step opts]
  (binding [*out* *err*]
    (println (bling [:blue.bold step])))
  (f step (update opts ::bc/steps (fnil conj []) step)))

#_{:clj-kondo/ignore [:unused-binding]}
(defn print-step-fn [f step {:keys [::action
                                    ::aero/module
                                    ::aero/profile
                                    ::lock/owner
                                    ::run/dir
                                    ::run/cmds
                                    ::bc/err
                                    ::bc/exit] :as opts}]
  (let [[lock-start-step] (lock/lock)
        [unlock-start-step] (unlock/unlock-any)
        [check-start-step] (git/check)
        prefix "\ueabc"
        msg (cond
              (= step ::read-module) (<< "Action {{ action }} | Module {{ module }} | Profile {{ profile }}")
              (= step ::mkdir) (<< "Making dir {{ dir }}")
              (= step lock-start-step) (<< "Lock (owner {{ owner }})")
              (= step unlock-start-step) (<< "Unlock any")
              (= step check-start-step) (<< "Checking if the working directory is clean {{ check-start-step }}")
              (= step ::compile-tf) (<< "Compiling {{ dir }}/main.tf.json")
              (= step ::run-cmd) (<< "Running:\n> {{ cmds | first }}")
              (= step ::push) (<< "Pushing last commit")
              :else nil)];
    (when msg
      (binding [*out* *err*]
        (println (bling [:green.bold (<< (str "{{ prefix }} " msg))])))))
  (let [{:keys [::bc/err
                ::bc/exit] :as opts} (f step opts)
        [_ check-end-step] (git/check)
        prefix "\uf05c"
        msg (cond
              (= step check-end-step) (<< "Working directory is NOT clean")
              (= step ::run-cmd) (<< "Failed running:\n> {{ cmds | first }}")
              :else nil)]
    (when (and msg (> exit 0))
      (println (bling [:red.bold (<< (str "{{ prefix }} " msg))])))
    opts))

(defn run-cmd [shell-opts {:keys [::bc/env ::run/cmds] :as opts}]
  (let [shell-opts (case env
                     :shell shell-opts
                     :repl (merge shell-opts {:out :string
                                              :err :string}))
        cmd (first cmds)
        proc (process/shell shell-opts cmd)]
    (handle-cmd opts proc)))

(defn run-cmds [step-fns {:keys [::run/dir ::run/extra-env] :as opts}]
  (let [wf (->workflow {:first-step ::run-cmd
                        :last-step ::run-cmd
                        :wire-fn (fn [step _]
                                   (case step
                                     ::run-cmd [(partial run-cmd {:continue true
                                                                  :extra-env extra-env
                                                                  :dir dir}) ::run-cmd]))
                        :next-fn (fn [_ _ {:keys [::run/cmds] :as opts}]
                                   (if (seq (rest cmds))
                                     [::run-cmd (merge opts {::run/cmds (rest cmds)})]
                                     [nil opts]))})]
    (wf step-fns opts)))

(defn compile-tf [opts]
  (let [{:keys [::bc/test-mode :big-config.tofu/fn :big-config.tofu/ns ::run/dir]} opts
        f (str dir "/main.tf.json")]
    (if test-mode
      (merge opts {::bc/exit 0
                   ::bc/err nil})
      (-> (format "%s/%s" ns fn)
          (symbol)
          requiring-resolve
          (apply (vector opts))
          (json/generate-string {:pretty true})
          (->> (spit f))
          (merge opts {::bc/exit 0
                       ::bc/err nil})))))

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
                                     ::run-cmds [(partial run-cmds step-fns) ::push]
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
      (:init :plan) (run-cmds step-fns opts)
      (:apply :destroy :ci) (wf step-fns opts))))

(defn ^:export main [{[action module profile] :args
                      step-fns :step-fns
                      env :env}]
  (let [action action
        module module
        profile profile
        step-fns (or step-fns [print-step-fn
                               (partial exit-step-fn ::end)])
        env (or env :shell)
        wf (->workflow {:first-step ::start
                        :wire-fn (fn [step step-fns]
                                   (case step
                                     ::start [action->opts ::read-module]
                                     ::read-module [aero/read-module ::mkdir]
                                     ::mkdir [mkdir ::compile-tf]
                                     ::compile-tf [compile-tf ::run-action]
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
                       ::aero/config "big-config.edn"
                       ::aero/module module
                       ::aero/profile profile})
         (into (sorted-map)))))

(comment
  (main {:args [:plan :alpha :dev]
         :step-fns [trace-step-fn
                    print-step-fn]
         :env :repl}))
