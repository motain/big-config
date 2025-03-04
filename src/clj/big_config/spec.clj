(ns big-config.spec
  (:require [clojure.spec.alpha :as s]))

(s/def ::ns string?)
(s/def ::fn string?)
(s/def ::owner string?)

(s/def ::config (s/keys :req-un [::ns ::fn]))
(s/def ::config-with-owner (s/keys :req-un [::ns ::owner]))
