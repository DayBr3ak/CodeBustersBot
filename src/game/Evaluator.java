package game;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static game.Utils.*;

public class Evaluator {
    private final GameParameters gameParameters;

    public Evaluator(GameParameters gameParameters) {
        this.gameParameters = gameParameters;
    }

    EvaluationState evaluate(
            Buster buster,
            Point newMyPosition,
            Point myBase,
            List<Buster> allies,
            List<Buster> enemies,
            List<Ghost> ghosts,
            Move move,
            Point checkPoint,
            Set<Integer> alreadyBusted
    ) {
        List<Buster> allBusters = new ArrayList<>(allies);
        allBusters.addAll(enemies);
        ghosts = moveGhosts(ghosts, move, allBusters, alreadyBusted);
        enemies = moveEnemies(enemies, myBase);



        boolean canBeStunned = checkCanBeStunned(newMyPosition, enemies);
        boolean iHaveStun = buster.remainingStunCooldown == 0;
        boolean isCarryingGhost = checkIsCarryingGhost(buster, move, ghosts);
        double distToCheckPoint = dist(newMyPosition, checkPoint);
        double distToBase = dist(newMyPosition, myBase);
        boolean inReleaseRange = distToBase <= gameParameters.RELEASE_RANGE;
        MovesAndDist movesToBustGhost = getMinMovesToBustGhost(newMyPosition, move, ghosts);
        boolean canStunEnemyWithGhost = checkCanStunEnemyWithGhost(buster, newMyPosition, enemies);
        return new EvaluationState(canBeStunned, iHaveStun, isCarryingGhost, distToCheckPoint, distToBase, inReleaseRange, movesToBustGhost, canStunEnemyWithGhost);
    }

    private boolean checkCanStunEnemyWithGhost(Buster buster, Point newMyPosition, List<Buster> enemies) {
        if (buster.remainingStunCooldown > 1) {
            return false;
        }
        for (Buster enemy : enemies) {
            if(enemy.isCarryingGhost && dist(newMyPosition, enemy) <= gameParameters.STUN_RANGE) {
                return true;
            }
        }
        return false;
    }

    private List<Buster> moveEnemies(List<Buster> enemies, Point myBase) {
        Point enemyBase = getEnemyBase(myBase, gameParameters);
        List<Buster> r = new ArrayList<>();
        Move toBase = Move.move(enemyBase);
        for (Buster enemy : enemies) {
            if (enemy.isCarryingGhost) {
                Point p = getNewPosition(enemy, toBase, gameParameters);
                //noinspection ConstantConditions
                r.add(new Buster(enemy.id, p.x, p.y, enemy.isCarryingGhost, enemy.remainingStunDuration, enemy.remainingStunCooldown));
            } else {
                r.add(enemy);
            }
        }
        return r;
    }

    private List<Ghost> moveGhosts(List<Ghost> ghosts, Move move, List<Buster> allBusters, Set<Integer> alreadyBusted) {
        List<Ghost> r = new ArrayList<>();
        for (Ghost ghost : ghosts) {
            r.add(moveGhost(ghost, move, allBusters, alreadyBusted));
        }
        return r;
    }

    private Point getMeanPoint(List<Buster> bustersWithMinDist) {
        double sumX = 0;
        double sumY = 0;
        for (Buster buster : bustersWithMinDist) {
            sumX += buster.x;
            sumY += buster.y;
        }
        int cnt = bustersWithMinDist.size();
        return Point.round(sumX / cnt, sumY / cnt);
    }

    private Ghost moveGhost(Ghost ghost, Move move, List<Buster> allBusters, Set<Integer> alreadyBusted) {
        if (ghost.bustCnt > 0) {
            return ghost;
        }
        if (alreadyBusted.contains(ghost.id)) {
            return ghost;
        }
        if (move.type == MoveType.BUST && move.targetId == ghost.id) {
            return ghost;
        }
        List<Buster> bustersWithMinDist = getBustersInRangeWithMinDist(ghost, allBusters);
        if (bustersWithMinDist.isEmpty()) {
            return ghost;
        }
        Point mean = getMeanPoint(bustersWithMinDist);
        Point p = Utils.runawayPoint(mean.x, mean.y, ghost.x, ghost.y, gameParameters.GHOST_MOVE_RANGE);
        return new Ghost(ghost.id, p.x, p.y, ghost.stamina, ghost.bustCnt);
    }

    private List<Buster> getBustersInRangeWithMinDist(Ghost ghost, List<Buster> allBusters) {
        double minDist = Double.POSITIVE_INFINITY;
        List<Buster> r = new ArrayList<>();
        for (Buster buster : allBusters) {
            double dist = dist(buster, ghost);
            if (dist > gameParameters.FOG_RANGE) {
                continue;
            }
            if (dist < minDist) {
                minDist = dist;
                r.clear();
                r.add(buster);
            } else if (dist == minDist) {
                r.add(buster);
            }
        }
        return r;
    }

    private MovesAndDist getMinMovesToBustGhost(Point newMyPosition, Move move, List<Ghost> ghosts) {
        MovesAndDist r = new MovesAndDist(99999, 0);
        for (Ghost ghost : ghosts) {
            MovesAndDist movesAndDist = getMinMovesToBustGhost(newMyPosition, move, ghost);
            if (movesAndDist.compareTo(r) < 0) {
                r = movesAndDist;
            }
        }
        return r;
    }

    private MovesAndDist getMinMovesToBustGhost(Point newMyPosition, Move move, Ghost ghost) {
        int moves = 0;
        moves += ghost.stamina;
        if (move.type == MoveType.BUST && move.targetId == ghost.id && ghost.stamina > 0) {
            moves--;
        }
        double pseudoDist = getPseudoDist(newMyPosition, ghost);
        moves += (int) Math.ceil(pseudoDist / gameParameters.MOVE_RANGE);
        double dist = dist(newMyPosition, ghost);
        return new MovesAndDist(moves, dist);
    }

    private double getPseudoDist(Point newMyPosition, Ghost ghost) {
        double dist = dist(newMyPosition, ghost);
        if (dist >= gameParameters.MAX_BUST_RANGE) {
            return dist - gameParameters.MAX_BUST_RANGE;
        }
        if (dist >= gameParameters.MIN_BUST_RANGE) {
            return 0;
        }
        return gameParameters.MIN_BUST_RANGE - dist;
    }

    private boolean checkIsCarryingGhost(Buster buster, Move move, List<Ghost> ghosts) {
        if (buster.isCarryingGhost) {
            return true;
        }
        if (move.type != MoveType.BUST) {
            return false;
        }
        for (Ghost ghost : ghosts) {
            if (ghost.id == move.targetId) {
                return ghost.stamina == 0; // todo or <= 1?
            }
        }
        throw new RuntimeException();
    }

    private boolean checkCanBeStunned(Point myPosition, List<Buster> enemies) {
        for (Buster enemy : enemies) {
            if (enemy.remainingStunDuration > 0) {
                continue;
            }
            if (dist(enemy, myPosition) <= gameParameters.STUN_RANGE + gameParameters.MOVE_RANGE) {
                return true;
            }
        }
        return false;
    }

    static class MovesAndDist implements Comparable<MovesAndDist> {
        private final int moves;
        private final double dist;

        MovesAndDist(int moves, double dist) {
            this.moves = moves;
            this.dist = dist;
        }

        @Override
        public int compareTo(MovesAndDist o) {
            if (moves != o.moves) {
                return Integer.compare(moves, o.moves);
            }
            return Double.compare(dist, o.dist);
        }

        @Override
        public String toString() {
            return "MovesAndDist{" +
                    "moves=" + moves +
                    ", dist=" + dist +
                    '}';
        }
    }
}
