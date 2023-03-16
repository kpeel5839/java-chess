package domain.piece;

import domain.coordinate.Position;
import domain.coordinate.Route;

import java.util.Collections;
import java.util.List;

public class InitPawn extends Piece {

    public InitPawn(final Color color) {
        super(color);
    }

    @Override
    public Route findRoute(final Position source, final Position target) {
        validateMovable(source, target);

        return new Route(getRoute(source, target));
    }

    private List<Position> getRoute(final Position source, final Position target) {
        if (target.diffY(source) == chooseDirection()) {
            return Collections.emptyList();
        }

        return List.of(source.move(0, chooseDirection()));
    }

    @Override
    protected boolean isMovable(final Position source, final Position target) {
        int direction = chooseDirection();

        int diffY = target.diffY(source);
        int diffX = target.diffX(source);

        return isPawnMovable(direction, diffY, diffX) || (diffY == direction * 2 && diffX == 0);
    }

    private static boolean isPawnMovable(final int direction, final int diffY, final int diffX) {
        return diffY == direction && (-1 <= diffX && diffX <= 1);
    }

    private int chooseDirection() {
        if (color == Color.WHITE) {
            return -1;
        }

        return 1;
    }
}