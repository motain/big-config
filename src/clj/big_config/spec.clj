(ns big-config.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::ns string?)
(s/def ::fn string?)
(s/def ::owner string?)

(s/def ::create  (s/keys :req-un [::ns ::fn]))
(s/def ::acquire (s/keys :req-un [::ns ::owner]))
(s/def ::release (s/keys :req-un [::ns]))
