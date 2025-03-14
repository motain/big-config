(ns big-config.main
  (:require
   [aero.core :as aero]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.run :as run]
   [big-config.run-with-lock :as rwl]
   [big-config.utils :refer [choice default-step-fn exit-end-fn generic-cmd
                             println-step-fn run-cmd step->message]]
   [cheshire.core :as json]
   [clojure.string :as str]))

(defn resolve-array [config key xs]
  (assoc config key
         (->> xs
              (map (fn [e] (if (keyword? e) (name (e config)) e)))
              (str/join ""))))

(defn read-module [cmd module profile]
  (let [config (-> (aero/read-config "big-config.edn" {:profile profile})
                   module
                   (merge {:cmd cmd
                           :module module
                           :profile profile}))
        {:keys [run-cmd working-dir]} config
        config (resolve-array config :working-dir working-dir)
        config (resolve-array config :run-cmd run-cmd)]
    config))

(read-module "apply" :module-a :dev)

(defn git-push [opts]
  (generic-cmd opts "git push"))

(defn run-with-lock
  ([opts]
   (run-with-lock default-step-fn opts))
  ([step-fn opts]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step ::rwl/lock-acquire
          opts opts]
     (let [[f next-step errmsg] (case step
                                  ::rwl/lock-acquire [(partial lock/lock step-fn) ::rwl/git-check "Failed to acquire the lock"]
                                  ::rwl/git-check [(partial git/check step-fn) ::rwl/run-cmd "The working directory is not clean"]
                                  ::rwl/run-cmd [run-cmd ::rwl/git-push "The command executed with the lock failed"]
                                  ::rwl/git-push [git-push ::rwl/lock-release-any-owner nil]
                                  ::rwl/lock-release-any-owner [(partial lock/unlock-any step-fn) ::rwl/end "Failed to release the lock"]
                                  ::rwl/end [identity nil])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (if next-step
           (choice {:on-success next-step
                    :on-failure ::rwl/end
                    :errmsg errmsg
                    :opts $})
           $))))))

(defn generate-main-tf-json [opts]
  (let [{:keys [fn ns working-dir]} opts
        f (str working-dir "/main.tf.json")]
    (try
      (-> (format "%s/%s" ns fn)
          (symbol)
          requiring-resolve
          (apply (vector opts))
          (json/generate-string {:pretty true})
          (->> (spit f))
          (merge opts {:exit 0
                       :err nil}))
      (catch Exception e
        (merge opts {:exit 1
                     :err (pr-str e)})))))

(defn run
  ([opts]
   (run default-step-fn opts))
  ([step-fn opts]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step ::run/generate-main-tf-json
          opts opts]
     (let [[f next-step] (case step
                           ::run/generate-main-tf-json [generate-main-tf-json ::run/run-cmd]
                           ::run/run-cmd [run-cmd ::run/end]
                           ::run/end [identity nil])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (if next-step
           (choice {:on-success next-step
                    :on-failure ::run/end
                    :opts $})
           $))))))

(defn run-step-fn [end {:keys [f step opts]}]
  (let [msg (step->message step)]
    (when msg
      (println msg))
    (let [new-opts (default-step-fn {:f f
                                     :step step
                                     :opts opts})]
      (when (= step end)
        (exit-end-fn new-opts))
      new-opts)))

(defn ^:export tofu [{[cmd module profile] :args
                      env :env}]
  (as-> (read-module cmd module profile) $
    (assoc $ :env (or env :shell))
    (case cmd
      (:init :plan) (run (partial run-step-fn ::run/end) $)
      :lock (do (println-step-fn :lock-acquire)
                (lock/lock $ (partial exit-end-fn "Failed to acquire the lock")))
      :unlock-any (do (println-step-fn :lock-release-any-owner)
                      (lock/unlock-any $))
      (:apply :destroy) (run-with-lock (partial run-step-fn ::rwl/end) $))))

(comment
  (-> (tofu {:args [:init :module-a :dev]
             :env :repl})))
