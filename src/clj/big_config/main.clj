(ns big-config.main
  (:require
   [babashka.process :as process]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.spec :as bs]
   [big-config.utils :refer [description-for-step error-for-step generic-cmd
                             handle-cmd recur-ok-or-end]]
   [cheshire.core :as json]
   [clojure.spec.alpha :as s]
   [com.bunimo.clansi :refer [style]]))

(defn print-and-flush
  [res]
  (println res)
  (flush))

(defn ^:export create [args]
  {:pre [(s/valid? ::bs/create args)]}
  (let [{:keys [fn ns]} args]
    (-> (format "%s/%s" ns fn)
        (symbol)
        requiring-resolve
        (apply (vector args))
        (json/generate-string {:pretty true})
        print-and-flush)))

(defn run-cmd [opts]
  (let [{:keys [run-cmd]} opts
        proc (process/shell {:continue true} run-cmd)]
    (handle-cmd opts proc)))

(defn git-push [opts]
  (generic-cmd opts "git push"))

(defn ^:export acquire-lock [opts]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :lock-acquire
         opts opts]
    (println (description-for-step step))
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (error-for-step step)]
      (case step
        :lock-acquire (as-> (lock/acquire opts) $
                        (recur-ok-or-end :exit $ error-msg))
        :exit opts))))

(defn ^:export release-lock-any-onwer [opts]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :lock-release-any-owner
         opts opts]
    (println (description-for-step step))
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (error-for-step step)]
      (case step
        :lock-release-any-owner (as-> (lock/release-any-owner opts) $
                                  (recur-ok-or-end :exit $ error-msg))
        :exit opts))))

(defn ^:export run-with-lock
  ([opts]
   (run-with-lock opts identity))
  ([opts end-fn]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step :lock-acquire
          opts opts]
     (when (not= :end step)
       (println (description-for-step step)))
     (let [opts (update opts :steps (fnil conj []) step)
           error-msg (error-for-step step)]
       (case step
         :lock-acquire (as-> (lock/acquire opts) $
                         (recur-ok-or-end :git-check $))
         :git-check (as-> (git/check opts) $
                      (recur-ok-or-end :run-cmd $ error-msg))
         :run-cmd (as-> (run-cmd opts) $
                    (recur-ok-or-end :git-push $ error-msg))
         :git-push (as-> (git-push opts) $
                     (recur-ok-or-end :lock-release $ error-msg))
         :lock-release (as-> (lock/release-any-owner opts) $
                         (recur-ok-or-end :end $))
         :end (end-fn opts))))))

(comment
  (create {:aws-account-id "251213589273"
           :region "eu-west-1"
           :ns "tofu.module-a.main"
           :fn "invoke"})
  #_{:clj-kondo/ignore [:unused-binding]}
  (let [opts {:aws-account-id "251213589273"
              :region "eu-west-1"
              :ns "tofu.module-a.main"
              :fn "invoke"
              :owner "ALBERTO_MACOS"
              :lock-keys [:aws-account-id :region :ns]
              :run-cmd "false"}
        end-fn (fn [{:keys [exit err] :as opts}]
                 (when (not= exit 0)
                   (-> err
                       (style :red)
                       println))
                 opts)]))
