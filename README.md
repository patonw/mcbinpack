# Monte Carlo Bin Packing

## Monte Carlo Tree Search
- Useful in situations where you take a sequence of actions and receive a score
- Commonly used to solve board games
- A form of stochastic optimization
- Related to Genetic Optimization, Simulated Annealing and Particle Swarm Optimization
- Well suited for problems with finite number of discrete actions
- State is defined by history of actions from initial state
- Outcome can be binary win/loss or numeric score
- Many similarities to reinforcement learning though different origin

## Bin Packing problems
- Fundamental to modern life
- Real world examples:
    - How to fit your entire family's luggage entire a car
    - How to pack an order into as few boxes as possible
    - Cutting a sheet of steel/wood/paper into rectangles selected from an approved list
    - Allocating batch tasks onto as few servers as possible
    - Filling a truck bed when moving so you trash or sell as little as possible
- Good concrete problem for demonstrating MCTS
- Concrete and familiar to most people
- Easy to visualize 2-D cases
- Not an easy problem theoretically
- NP-complete meaning probably no efficient way to solve exactly
- However, relatively easy to solve approximately

- This demo addresses the case of packing items in one bin to minimize wasted space

### Bin Packing Heuristics
- Shelf algorithms
    - Partitions a bin into parallel shelves
    - Height of shelf determined by tallest item in it
    - Turns 2-D problem into multiple 1-D problems
        - First, how to pack items into shelves by width
        - Then, how to pack shelves into bin by height
    - Overall, try to pack items into shelves to minimize cumulative height
    - Works fairly well in practice
    - Still ends up with a lot of wasted space on each shelf
    - Can easily reorder shelves within a bin or swap between bins
- Guillotine algorithms
    - Rather than parallel shelves, split bin vertically or horizontally
    - Continue to split each subdivision recursively
    - Determine where to split a bin by first inserting an item to one corner
    - Split along either horizontal or vertical edge of item
    - Need two selection criteria now:
        - Which empty bin to insert item into
        - Which axis to split the bin along
    - Empty space remains adjacent to item after split
    - Can perform secondary split to track it
    - Dimensions of vacancies determined by split ordering, even though area is the same
    - Can end up with a skinny and fat rectangle, or a large and small squarish bin
    - Less wasted space than shelves
    - Infeasible to reorder splits
    - Subdivisions can be swapped between bins, but now constrained on two dimensions

- Vacancy selection criteria
    - First fit
        - Track unordered list or stack of vacancies
        - Filter out vacancies that are two small in one or both dimensions
        - Pick the first remaining one
    - Best fit
        - Find a vacancy with that will result in the least amount of remaining space
        - Can measure space along a linear axis or as area
        - This greedily minimizes "wasted" space
    - Worst fit
        - Conversely, pick the vacancy that results in the most remaining "space"
        - This tries to leave the remaining space as flexible as possible for future items
    - Random
        - Essentially first fit but shuffles the list every selection
        - Removes effect of insertion order in implementation
        - Otherwise might favor most recently created or oldest vacancies

## MCTS In-Depth
- With problem definition on hand, start with an empty bin and list of items
- This is the root of the search tree
- Select an item to insert and pick an axis for the primary split
- Select another item
- Select one of the vacancies
- Choose an axis for the primary split again
- Repeat until no more items can be inserted

- At each step, we need to decide:
    - What item to insert
    - Where to insert it
    - Which way to split the bin

- Pretend insertion order and vacancy selection are predetermined
    - Only deciding on vertical or horizontal split remains
    - Two paths forward from current step
    - From each of those two further paths exist, total of four
    - Each of those four has two paths, totaling 8 and so on
    - This is a (binary) tree
- The task is to navigate this tree to the best possible solution
- Generalizes naturally to multiple decisions at each level
    - If you have `k` vacancies and 2 split axes then `2k` branches from node

### On Simulation and Selection
- Starting from the top with no prior knowledge
    - All branches look the same
    - Only sensible thing to do is pick one arbitrarily and randomly walk down the tree
    - Until get to a point where you can calculate score or win/loss
    - This is "simulation"
    - If you only have one sample, you can't tell how good or bad it is relative to alternatives
    - From that first choice do more random walks, tracking scores
    - This gives you a distribution with mean, variance and optima
- Pick a different first step and randomly sample to get mean, variance and optima
    - Now you can compare two choices from the top
    - Still a lot of uncertainty from a handful of samples along each path
    - Scores reflect random walk rather than optimal behavior, though
    - Regardless, gives us some information about the relative value of choices at a branch
- Can prune out choices that are obviously or probably bad from the top
- Spend more time focused on more "promising" choices
- If we're reasonably confident about a handful of choices...
    - Step down and start scrutinizing choices branching of from them
    - Select a second action at random and perform several random walks from there
    - Track score statistics for each second choice
- Now back from the two, we have more information on choices across two levels
    - There is a rub however
    - On some branches off the root we have a lot of information on others, very little
    - We're tracking mean and variance on each of these choices
    - Some branches are no longer purely random walks
    - Since we're making a second choice from them, the statistics will be skewed slightly towards optimum
    - Not because the choice is better, but because we spend more time sampling "better" second level subtrees
    - Can try to weight the mean, but that becomes quite complex
    - Instead, on each node, track stats for pure random walk separately from deliberate walks down subtree

    
### Node Selection Criteria
- The deeper down the tree we move, the lower the variance of random walks from each node
- However, there are exponentially more nodes to consider
- Again we need to be judicious about not wasting time on unpromising paths
    - We want to take as few samples from them to decide whether to ignore them
    - Can use likelihood statistics and confidence intervals (more later)
- What happened to optima (min/max)?
    - Ultimately we want to move towards an optimum
    - In random sampling however, these are such extreme outliers
    - If we greedily select paths based on optima we will quickly get stuck
    - However, they still do give us a lot of information about the score distribution
- Hybrid approach to use both confidence intervals and optima
    - Possible to use likelihood of global optimum against each branch to select path

## Visualization
- Two dimensional bin packing is trivial to visualize
- Can spot filled vs empty space easily
- D3.js is a good tool for rendering
- Charting score history with Vega-Lite
- Text box for tweaking solver parameters and problem specification

## Implementation Notes
### Annealing Schedules
- Some degree of randomness in selection is possible
- Early on, LCB and optima for each node is +/- infinity
- Later however, we might get stuck using greedy selection based on LCB and optima
- Can occasionally inject randomness to explore alternative paths
- How much randomness at each step is determined by an annealing schedule
- MCTS implementation uses a `focus` parameter
- Focus of 0 is completely random but 1.0 is completely greedy
- Available schedules are:
    - constant: starts at `alpha` and never changes
    - exponential: starts at `alpha` and increases to 1.0 exponentially
    - many more

### Sampling and Confidence Intervals
- When sampling a random variable one can pretend that there is some "true" value distorted by error
- Confidence intervals provide bounds for where we think the ground truth might be
- Width of confidence interval determined by variance and number of samples
- If we only have a few samples, we don't really have a good idea of how much error is distorting the measurement
- If we have many samples, even with large variance, we can more confidently say where the ground truth is
    - While quantifying how large the distortion of the measurement is

### Concurrency
#### Immutability
- If concurrent tasks share no data, then concurrency is trivial
- If concurrent tasks read from same fixed data, no problems with consistency
- Problems arise when trying to modify data consistently
- One task may be in the middle of modifying data when another changes it
- Now state is messed up
- Traditional systems approach is to use locks

#### Optimistic Locking with Atomics
- Synchronization is one method to make concurrent modification safe
- However, expensive
- Read/write locks are an improvement but still overkill
- If collisions are relatively rare, can try optimistic locking
- Makes use of CPU level support for atomic operations
- Queue a change assuming nothing else has modified data
- Tell CPU what you think the data was and what you want it to be
- If data was unmodified before, operation succeeds and change is committed
- Otherwise operation fails, so retry until success
- When failures are rare multiple retries are improbable
- Even if cost of recomputing the change for retry is high
    - Amortized cost is low compared to pessimistic locking every operation
- A gotcha: What if I need to concurrently modify two variables?
    - What if there is some dependence between them
    - Can we make both atomic?
    - What happens one one update succeeds and the other fails?
    - Now state is inconsistent
    - Solution: atomic references to data objects
    - Values in data object must always be consistent
    - Only the reference is modified atomically

## TODO
- [ ] Create a writeup from notes
- [ ] Ensembling carousel