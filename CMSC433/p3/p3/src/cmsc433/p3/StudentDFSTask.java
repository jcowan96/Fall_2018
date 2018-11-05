package cmsc433.p3;

import java.util.concurrent.Callable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class StudentDFSTask implements Callable<List<Direction>> {
    private Choice choice;
    private Direction firstDir;
    private Direction secondDir;
    private StudentMTMazeSolver solver;

    //1 Direction Constructor
    public StudentDFSTask(Choice ch, Direction dir, StudentMTMazeSolver smms) {
        this.choice = ch;
        this.firstDir = dir;
        this.secondDir = null;
        this.solver = smms;
    }

    //2 Direction Contructor (2nd degree)
    public StudentDFSTask(Choice ch, Direction dir1, Direction dir2, StudentMTMazeSolver smms) {
        this.choice = ch;
        this.firstDir = dir1;
        this.secondDir = dir2;
        this.solver = smms;
    }

    //Essentially copy this method from STMazeSolverDFS, modified slightly to start at the
    //Choice where the task is being invoked, as opposed to the beginning of the maze.
    //Implements Callable because it wants to return a value (the list of directions potentially
    //leading to the maze exit)
    @Override
    public List<Direction> call() {
        LinkedList<Choice> choiceStack = new LinkedList<>();
        Choice ch;

        try {
            //Push choice Task was instantiated with onto stack, not first in maze
            choiceStack.push(choice);
            while (!choiceStack.isEmpty()) {
                ch = choiceStack.peek();
                if (ch.isDeadend()) {
                    //Backtrack to next choice on stack, pop next choice from their list of choices
                    choiceStack.pop();
                    if (!choiceStack.isEmpty()) choiceStack.peek().choices.pop();
                    continue;
                }
                choiceStack.push(solver.follow(ch.at, ch.choices.peek()));
            }
            //If stack runs out of choice with no solution found, return null
            return null;
        }
        catch (SkippingMazeSolver.SolutionFound e) {
            Iterator<Choice> iter = choiceStack.iterator();
            LinkedList<Direction> solutionPath = new LinkedList<>();
            while (iter.hasNext()) {
                ch = iter.next();
                solutionPath.push(ch.choices.peek());
            }
            //Initial direction from start of maze is at front of list
            if (secondDir != null) solutionPath.push(secondDir);
            solutionPath.push(firstDir);

            if (solver.maze.display != null) solver.maze.display.updateDisplay();
            return solver.pathToFullPath(solutionPath);
        }
    }
}
