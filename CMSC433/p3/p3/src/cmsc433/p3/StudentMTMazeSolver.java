package cmsc433.p3;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;


/**
 * This file needs to hold your solver to be tested.
 * You can alter the class to extend any class that extends MazeSolver.
 * It must have a constructor that takes in a Maze.
 * It must have a solve() method that returns the datatype List<Direction>
 *   which will either be a reference to a list of steps to take or will
 *   be null if the maze cannot be solved.
 */
public class StudentMTMazeSolver extends SkippingMazeSolver
{
    public StudentMTMazeSolver(Maze maze)
    {
        super(maze);
    }

    public List<Direction> solve()
    {
        //If the maze is small enough, might as well avoid the overhead of threads and use the
        //DFS version (which tends to be the fastest for these smaller mazes). Small optimization, but
        //should be worth it if solving many small mazes
//        if (maze.getWidth() < 500 && maze.getHeight() < 500) {
//            STMazeSolverDFS DFS = new STMazeSolverDFS(maze);
//            return DFS.solve();
//        }

        //Create/initialize data structures
        //Take advantage of all physical processors on system, but min of 2 threads
        int numProcessors = Runtime.getRuntime().availableProcessors();
        ForkJoinPool fJPool = new ForkJoinPool(Math.max(2, numProcessors));
        List<Direction> finalPath; //Final list of directions to solve the maze, or null if none exists
        ArrayList<StudentDFSTask> studentTasks = new ArrayList<>(); //Hold tasks to invoke all at once
        List<Future<List<Direction>>> returnedTasks = new LinkedList<>(); //Hold results of tasks

        //Create a thread for each choice branch at the start of the maze, and send them off on a DFS
        //from that point. Breaking up into tasks here strikes a good balance between not taking advantage
        //of parallelism and using too many resources creating/managing threads
        try {
            Choice first = firstChoice(maze.getStart());
            int numChoices = first.choices.size(); //Save this var to prevent popping affecting this loop
            int i = 0;

            while (i < numChoices){
                Direction nextDir = first.choices.pop();
                Choice ch = follow(first.at, nextDir);
                studentTasks.add(new StudentDFSTask(ch, nextDir, this));

                //If the maze is huge, also branch on the 2nd degree choices
                if (maze.getWidth() >= 4000 || maze.getHeight() >= 4000) {
                    for (int x = 0; x < ch.choices.size(); x++) {
                        Direction nextNextDir = ch.choices.pop();
                        Choice ch2 = follow(ch.at, nextNextDir);
                        studentTasks.add(new StudentDFSTask(ch2, nextDir, nextNextDir, this));
                    }
                }
                i++;
            }
        }
        catch (SolutionFound e) {
            System.out.println("Solution found 1 step away from the start of the maze; this is very unlikely to happen");
        }

        //Invoke all tasks (which is why they implement Callable) and shutdown the pool, waiting for all
        //tasks to finish before continuing
        try {
            returnedTasks = fJPool.invokeAll(studentTasks);
        }
        catch (NullPointerException e) { System.out.println("Null Pointer Exception"); }
        catch (RejectedExecutionException e) { System.out.println("Rejected Execution Exception"); }

        fJPool.shutdown(); //Shutdown pool and wait for all tasks to join; don't accept any new ones

        //Look through the list of returned tasks and check them to see which ones have a value. If
        //none do there is no solution to the maze; return null. If any of the lists are non-null, it
        //is the (one and only) solution to the maze; return it
        Iterator<Future<List<Direction>>> retIter = returnedTasks.iterator();
        while (retIter.hasNext()) {
            Future<List<Direction>> list = retIter.next();
            try {
                if (list.get() == null) {
                    //If the list is null the task couldn't find a solution
                    //Do nothing
                }
                else {
                    //If list.get() exists, the solution has been found
                    finalPath = list.get();
                    //System.out.println(finalPath);
                    return finalPath;
                }
            }
            catch (InterruptedException e) { System.out.println("Interrupted Exception when finding successful path"); }
            catch (ExecutionException e) { System.out.println("Execution Exception when finding successful path"); }
        }

        //If code makes it to this point no viable path has been found, return null maze is unsolvable
        return null;
    }
}
