(ns harold-paint.color-picker
  (:require [reagent.core :as reagent]
            [goog.events :as events])
  (:import [goog.events EventType]))

(defn- filled-circle
  [canvas-ctx x y r color]
  (aset canvas-ctx "fillStyle" color)
  (.beginPath canvas-ctx)
  (.arc canvas-ctx x y r 0 (* 2 Math/PI))
  (.fill canvas-ctx))

(defn- render
  [ctx]
  (let [{:keys [canvas-ctx color]} (-> ctx :state* deref)
        [r g b] color
        w (-> canvas-ctx .-canvas .-clientWidth)
        h (-> canvas-ctx .-canvas .-clientHeight)]
    (dotimes [i 16]
      (aset canvas-ctx "fillStyle" (str "rgb("(* 16 i)","g","b")"))
      (.fillRect canvas-ctx 0 (* (- 15 i) (quot h 16)) (quot w 9) (quot h 16)))
    (filled-circle canvas-ctx (quot w 18) (- h (* 1.0 h (/ r 255))) 6 "#000000")
    (filled-circle canvas-ctx (quot w 18) (- h (* 1.0 h (/ r 255))) 4 "#ffffff")
    (dotimes [i 16]
      (aset canvas-ctx "fillStyle" (str "rgb("r","(* 16 i)","b")"))
      (.fillRect canvas-ctx (quot w 9) (* (- 15 i) (quot h 16)) (quot w 9) (quot h 16)))
    (filled-circle canvas-ctx (* 3 (quot w 18)) (- h (* 1.0 h (/ g 255))) 6 "#000000")
    (filled-circle canvas-ctx (* 3 (quot w 18)) (- h (* 1.0 h (/ g 255))) 4 "#ffffff")
    (dotimes [i 16]
      (aset canvas-ctx "fillStyle" (str "rgb("r","g","(* 16 i)")"))
      (.fillRect canvas-ctx (* 2 (quot w 9)) (* (- 15 i) (quot h 16)) (quot w 9) (quot h 16)))
    (filled-circle canvas-ctx (* 5 (quot w 18)) (- h (* 1.0 h (/ b 255))) 6 "#000000")
    (filled-circle canvas-ctx (* 5 (quot w 18)) (- h (* 1.0 h (/ b 255))) 4 "#ffffff")
    (aset canvas-ctx "fillStyle" (str "rgb("r","g","b")"))
    (.fillRect canvas-ctx (quot w 3) 0 (quot w 3) h)))

(defn- on-click
  [ctx e]
  (let [state* (:state* ctx)
        {:keys [canvas-ctx color]} @state*
        [r g b] color
        w (-> canvas-ctx .-canvas .-clientWidth)
        h (-> canvas-ctx .-canvas .-clientHeight)
        x (.-offsetX e)
        color-index (cond
                      (< 0 x (quot w 9)) 0
                      (< (quot w 9) x (* 2 (quot w 9))) 1
                      (< (* 2 (quot w 9)) x (* 3 (quot w 9))) 2
                      :else nil)
        y (.-offsetY e)
        p (- 1.0 (/ y h))]
    (when color-index
      (swap! state* assoc
             :color (assoc color color-index (int (* p 255))))
      (render ctx))))

(defn color-picker
  [props]
  (let [color (or (:default-value props) [255 0 0])
        ctx {:state* (reagent/atom {:color color})
             :props props}]
    (reagent/create-class
     {:component-did-mount
      (fn [this]
        (let [canvas (-> (reagent/dom-node this)
                         (.querySelectorAll "canvas")
                         (aget 0))]
          (swap! (:state* ctx) assoc
                 :canvas-ctx (.getContext canvas "2d")
                 :mouse-down-key (events/listen canvas (.-MOUSEDOWN EventType) (partial on-click ctx))))
        (render ctx))
      :reagent-render
      (fn [props]
        [:div.color-picker
         [:canvas {:width 384 :height 128}]])})))
