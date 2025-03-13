(ns big-config.main
  (:require
   [aero.core :as aero]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.utils :refer [default-step-fn exit-end-fn generic-cmd
                             println-step-fn recur-ok-or-end run-cmd
                             step->message]]
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
   (loop [step :lock-acquire
          opts opts]
     (let [[f next-step err-msg] (case step
                                   :lock-acquire [lock/acquire :git-check "Failed to acquire the lock"]
                                   :git-check [git/check :run-cmd "The working directory is not clean"]
                                   :run-cmd [run-cmd :git-push "The command executed with the lock failed"]
                                   :git-push [git-push :lock-release-any-owner nil]
                                   :lock-release-any-owner [lock/release-any-owner :end "Failed to release the lock"]
                                   :end [identity nil])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (if next-step
           (recur-ok-or-end next-step $ err-msg)
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
   (loop [step :generate-main-tf-json
          opts opts]
     (let [[f next-step] (case step
                           :generate-main-tf-json [generate-main-tf-json :run-cmd]
                           :run-cmd [run-cmd :end]
                           :end [identity nil])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (if next-step
           (recur-ok-or-end next-step $)
           $))))))

(defn env-step-fn [{:keys [f step opts]}]
  (when (not= step :end)
    (println (step->message step)))
  (let [new-opts (default-step-fn {:f f
                                   :step step
                                   :opts opts})]
    (when (= step :end)
      (exit-end-fn new-opts))
    new-opts))

(defn ^:export tofu [{[cmd module profile] :args
                      env :env}]
  (as-> (read-module cmd module profile) $
    (assoc $ :env (or env :shell))
    (case cmd
      (:init :plan) (run env-step-fn $)
      :lock (do (println-step-fn :lock-acquire)
                (lock/acquire $ (partial exit-end-fn "Failed to acquire the lock")))
      :unlock-any (do (println-step-fn :lock-release-any-owner)
                      (lock/release-any-owner $))
      (:apply :destroy) (run-with-lock env-step-fn $))))

(comment
  (-> (tofu {:args [:init :module-a :dev]
             :env :repl})))
