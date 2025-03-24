(ns big-config.run
  (:require
   [babashka.process :as process]
   [big-config :as bc]
   [big-config.core :refer [->workflow]]
   [clojure.string :as str]))

(def default-opts {:continue true
                   :out :string
                   :err :string})

(defn handle-cmd [opts proc]
  (let [res (-> (select-keys proc [:exit :out :err :cmd])
                (update-vals (fn [v] (if (string? v)
                                       (str/replace v #"\x1B\[[0-9;]+m" "")
                                       v))))]

    (-> opts
        (update ::bc/procs (fnil conj []) res)
        (merge (-> res
                   (select-keys [:exit :err])
                   (update-keys (fn [k] (keyword "big-config" (name k)))))))))

(defn generic-cmd
  ([opts cmd]
   (let [proc (process/shell default-opts cmd)]
     (handle-cmd opts proc)))
  ([opts cmd key]
   (let [proc (process/shell default-opts cmd)]
     (-> opts
         (assoc key (-> (:out proc)
                        str/trim-newline))
         (handle-cmd proc)))))

(defn run-cmd [{:keys [::bc/env ::shell-opts ::cmds] :as opts}]
  (let [shell-opts (case env
                     :shell shell-opts
                     :repl (merge shell-opts {:out :string
                                              :err :string}))
        cmd (first cmds)
        proc (process/shell shell-opts cmd)]
    (handle-cmd opts proc)))

(def run-cmds
  (->workflow {:first-step ::run-cmd
               :last-step ::run-cmd
               :wire-fn (fn [step _]
                          (case step
                            ::run-cmd [run-cmd ::run-cmd]))
               :next-fn (fn [_ _ {:keys [::cmds] :as opts}]
                          (if (seq (rest cmds))
                            [::run-cmd (merge opts {::cmds (rest cmds)})]
                            [nil opts]))}))

(comment
  (run-cmds  {::bc/env :repl
              ::shell-opts {:continue true}
              ::cmds ["echo one"
                      "echo two"
                      "echo three"]}))
