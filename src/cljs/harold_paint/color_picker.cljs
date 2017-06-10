(ns harold-paint.color-picker
  (:require [reagent.core :as reagent]))

(defn- render
  [ctx]
  (let [{:keys [canvas-ctx color]} (-> ctx :state* deref)
        [r g b] color
        w (-> canvas-ctx .-canvas .-clientWidth)
        h (-> canvas-ctx .-canvas .-clientHeight)]
    (aset canvas-ctx "fillStyle" (str "rgb("r","g","b")"))
    (.fillRect canvas-ctx 0 0 w h)))

(defn color-picker
  [props]
  (let [color (or (:default-value props) [255 0 0])
        ctx {:state* (reagent/atom {:color color})
             :props props}]
    (reagent/create-class
     {:component-did-mount
      (fn [this]
        (swap! (:state* ctx) assoc
               :canvas-ctx (-> (reagent/dom-node this)
                               (.querySelectorAll "canvas")
                               (aget 0)
                               (.getContext "2d")))
        (render ctx))
      :reagent-render
      (fn [props]
        [:div.color-picker
         [:canvas {:width 128 :height 128}]])})))
