/**
 * @author @francoismarot
 * @see https://gist.github.com/fmarot/f04346d0e989baef1f56ffd83bbf764d
 */

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JPanel;

/**
 * Un Layout Swing qui va réduire ou agrandir le contenu d'un conteneur tout en
 * gardant son ratio d'aspect. L'inconvénient est qu'un seul composant est supporté
 * sinon une exception sera lancée.
 * C'est la méthode getPreferredSize() du composant qui doit retourner le bon ratio.
 * La taille préférée ne sera pas préservée mais le ratio le sera.
 * @author @francoismarot
 * @see https://gist.github.com/fmarot/f04346d0e989baef1f56ffd83bbf764d
 */
public class AspectRatioKeeperLayout implements LayoutManager {

	/** Sera utilisé pour les calculs au cas où aucun composant réel n'est dans le parent */
	private static Component fakeComponent = new JPanel();

	public AspectRatioKeeperLayout() {
		fakeComponent.setPreferredSize(new Dimension(0, 0));
	}

	@Override
	public void addLayoutComponent(String arg0, Component arg1) {
	}

	@Override
	public void layoutContainer(Container parent) {
		Component component = getSingleComponent(parent);
		Insets insets = parent.getInsets();
		int maxWidth = parent.getWidth() - (insets.left + insets.right);
		int maxHeight = parent.getHeight() - (insets.top + insets.bottom);

		Dimension prefferedSize = component.getPreferredSize();
		Dimension targetDim = getScaledDimension(prefferedSize, new Dimension(maxWidth, maxHeight));

		double targetWidth = targetDim.getWidth();
		double targetHeight = targetDim.getHeight();

		double hgap = (maxWidth - targetWidth) / 2;
		double vgap = (maxHeight - targetHeight) / 2;

		// Définir la taille et la position du composant unique.
		component.setBounds((int) hgap, (int) vgap, (int) targetWidth, (int) targetHeight);
	}

	private Component getSingleComponent(Container parent) {
		int parentComponentCount = parent.getComponentCount();
		if (parentComponentCount > 1) {
			throw new IllegalArgumentException(this.getClass().getSimpleName()
					+ " ne peut pas gérer plus d'un composant");
		}
		Component comp = (parentComponentCount == 1) ? parent.getComponent(0) : fakeComponent;
		return comp;
	}

	private Dimension getScaledDimension(Dimension imageSize, Dimension boundary) {
		double widthRatio = boundary.getWidth() / imageSize.getWidth();
		double heightRatio = boundary.getHeight() / imageSize.getHeight();
		double ratio = Math.min(widthRatio, heightRatio);
		return new Dimension((int) (imageSize.width * ratio), (int) (imageSize.height * ratio));
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		return preferredLayoutSize(parent);
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		return getSingleComponent(parent).getPreferredSize();
	}

	@Override
	public void removeLayoutComponent(Component parent) {
	}
} 