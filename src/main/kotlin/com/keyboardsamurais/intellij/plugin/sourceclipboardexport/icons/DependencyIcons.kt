package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.icons

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import javax.swing.Icon

/**
 * Icons for dependency visualization using circles and directional arrows
 */
object DependencyIcons {

    val DIRECT_IMPORTS: Icon = DirectImportsIcon()
    val TRANSITIVE_IMPORTS: Icon = TransitiveImportsIcon()
    val DEPENDENTS: Icon = DependentsIcon()
    val BIDIRECTIONAL: Icon = BidirectionalIcon()

    private abstract class BaseDepIcon : Icon {
        override fun getIconWidth() = 16
        override fun getIconHeight() = 16

        protected fun createCircle(x: Double, y: Double, radius: Double): Ellipse2D.Double {
            return Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2)
        }
        
        protected fun createArrow(x1: Double, y1: Double, x2: Double, y2: Double): Path2D.Double {
            val arrow = Path2D.Double()
            arrow.moveTo(x1, y1)
            arrow.lineTo(x2, y2)
            
            // Add arrowhead
            val angle = Math.atan2(y2 - y1, x2 - x1)
            val arrowLength = 3.5
            val arrowAngle = Math.PI / 6
            
            arrow.moveTo(x2, y2)
            arrow.lineTo(
                x2 - arrowLength * Math.cos(angle - arrowAngle),
                y2 - arrowLength * Math.sin(angle - arrowAngle)
            )
            arrow.moveTo(x2, y2)
            arrow.lineTo(
                x2 - arrowLength * Math.cos(angle + arrowAngle),
                y2 - arrowLength * Math.sin(angle + arrowAngle)
            )
            
            return arrow
        }
        
        protected fun getStrokeColor(): Color = if (UIUtil.isUnderDarcula()) {
            JBColor(Color(180, 180, 180), Color(180, 180, 180))
        } else {
            JBColor(Color(60, 60, 60), Color(60, 60, 60))
        }
        
        protected fun getSelectedFileColor(): Color = if (UIUtil.isUnderDarcula()) {
            JBColor(Color(100, 150, 200, 100), Color(100, 150, 200, 100))
        } else {
            JBColor(Color(70, 130, 180, 100), Color(70, 130, 180, 100))
        }
        
        protected fun getDependencyColor(): Color = if (UIUtil.isUnderDarcula()) {
            JBColor(Color(100, 200, 100, 80), Color(100, 200, 100, 80))
        } else {
            JBColor(Color(50, 180, 50, 80), Color(50, 180, 50, 80))
        }
        
        protected fun getReverseDependencyColor(): Color = if (UIUtil.isUnderDarcula()) {
            JBColor(Color(200, 100, 100, 80), Color(200, 100, 100, 80))
        } else {
            JBColor(Color(200, 50, 50, 80), Color(200, 50, 50, 80))
        }
        
        protected fun getArrowColor(): Color = if (UIUtil.isUnderDarcula()) {
            JBColor(Color(160, 160, 160), Color(160, 160, 160))
        } else {
            JBColor(Color(80, 80, 80), Color(80, 80, 80))
        }
    }

    /**
     * Icon for "Include Direct Dependencies" - selected file with single arrow to dependency
     */
    private class DirectImportsIcon : BaseDepIcon() {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(x, y)

            // Selected file (left)
            val selectedCircle = createCircle(4.5, 8.0, 3.5)
            g2.color = getSelectedFileColor()
            g2.fill(selectedCircle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.5f)
            g2.draw(selectedCircle)
            
            // Direct dependency (right)
            val dependencyCircle = createCircle(11.5, 8.0, 3.5)
            g2.color = getDependencyColor()
            g2.fill(dependencyCircle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.0f)
            g2.draw(dependencyCircle)
            
            // Arrow from selected to dependency
            val arrow = createArrow(7.0, 8.0, 9.0, 8.0)
            g2.color = getArrowColor()
            g2.stroke = BasicStroke(1.5f)
            g2.draw(arrow)
            
            g2.dispose()
        }
    }

    /**
     * Icon for "Include Transitive Dependencies" - chain of dependencies
     */
    private class TransitiveImportsIcon : BaseDepIcon() {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(x, y)

            // Selected file (left)
            val selectedCircle = createCircle(3.0, 8.0, 2.5)
            g2.color = getSelectedFileColor()
            g2.fill(selectedCircle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.5f)
            g2.draw(selectedCircle)
            
            // First dependency
            val dep1Circle = createCircle(8.0, 8.0, 2.0)
            g2.color = getDependencyColor()
            g2.fill(dep1Circle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.0f)
            g2.draw(dep1Circle)
            
            // Second dependency (transitive)
            val dep2Circle = createCircle(13.0, 8.0, 2.0)
            g2.color = getDependencyColor().brighter()
            g2.fill(dep2Circle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(0.8f)
            g2.draw(dep2Circle)
            
            // Arrows showing transitive chain
            g2.color = getArrowColor()
            g2.stroke = BasicStroke(1.3f)
            g2.draw(createArrow(5.0, 8.0, 6.5, 8.0))
            g2.stroke = BasicStroke(1.0f)
            g2.draw(createArrow(9.5, 8.0, 11.5, 8.0))
            
            g2.dispose()
        }
    }

    /**
     * Icon for "Include Reverse Dependencies" - arrow pointing to selected file
     */
    private class DependentsIcon : BaseDepIcon() {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(x, y)

            // Selected file (right)
            val selectedCircle = createCircle(11.5, 8.0, 3.5)
            g2.color = getSelectedFileColor()
            g2.fill(selectedCircle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.5f)
            g2.draw(selectedCircle)
            
            // Reverse dependency (left)
            val reverseDependencyCircle = createCircle(4.5, 8.0, 3.5)
            g2.color = getReverseDependencyColor()
            g2.fill(reverseDependencyCircle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.0f)
            g2.draw(reverseDependencyCircle)
            
            // Arrow from reverse dependency to selected
            val arrow = createArrow(7.0, 8.0, 9.0, 8.0)
            g2.color = getArrowColor()
            g2.stroke = BasicStroke(1.5f)
            g2.draw(arrow)
            
            g2.dispose()
        }
    }

    /**
     * Icon for "Include Bidirectional Dependencies" - arrows in both directions
     */
    private class BidirectionalIcon : BaseDepIcon() {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(x, y)

            // Selected file (center)
            val selectedCircle = createCircle(8.0, 8.0, 3.0)
            g2.color = getSelectedFileColor()
            g2.fill(selectedCircle)
            
            // Left circle (reverse dependencies)
            val leftCircle = createCircle(3.0, 8.0, 2.5)
            g2.color = getReverseDependencyColor()
            g2.fill(leftCircle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.0f)
            g2.draw(leftCircle)
            
            // Right circle (dependencies)
            val rightCircle = createCircle(13.0, 8.0, 2.5)
            g2.color = getDependencyColor()
            g2.fill(rightCircle)
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.0f)
            g2.draw(rightCircle)
            
            // Draw selected file outline last so it's on top
            g2.color = getStrokeColor()
            g2.stroke = BasicStroke(1.5f)
            g2.draw(selectedCircle)
            
            // Left arrow (incoming)
            g2.color = getArrowColor()
            g2.stroke = BasicStroke(1.3f)
            g2.draw(createArrow(4.5, 7.0, 6.0, 7.0))
            
            // Right arrow (outgoing)
            g2.draw(createArrow(10.0, 9.0, 11.5, 9.0))
            
            g2.dispose()
        }
    }
}