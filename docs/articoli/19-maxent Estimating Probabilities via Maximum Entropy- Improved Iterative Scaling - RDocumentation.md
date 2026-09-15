> Source: https://www.rdocumentation.org/packages/FD/versions/1.0-12.5/topics/maxent

maxent function - RDocumentation
[
Last chance: 50% off unlimited learning
Sale ends in 2 d 10 h 07 m 18 s Buy Now](https://www.datacamp.com/promo/flash-sale-september-26)
[
Rdocumentation
](https://www.rdocumentation.org/)
powered by 
Learn R Programming
FD (version 1.0-12.5)
maxent: Estimating Probabilities via Maximum Entropy: Improved Iterative Scaling
Description
maxent returns the probabilities that maximize the entropy conditional on a series of constraints that are linear in the features. It relies on the Improved Iterative Scaling algorithm of Della Pietra et al. (1997). It has been used to predict the relative abundances of a set of species given the trait values of each species and the community-aggregated trait values at a site (Shipley et al. 2006; Shipley 2009; Sonnier et al. 2009).
Usage
Value
prob
vector of predicted probabilities
moments
vector of final moments
entropy
Shannon entropy of prob
iter
number of iterations required to reach convergence
lambda
λ -values, only returned if lambda = T
constr
macroscopical constraints
states
states and their attributes
prior
prior probabilities
Arguments
constr
vector of macroscopical constraints (e.g. community-aggregated trait values). Can also be a matrix or data frame, with constraints as columns and data sets (e.g. sites) as rows.
states
vector, matrix or data frame of states (columns) and their attributes (rows).
prior
vector, matrix or data frame of prior probabilities of states (columns). Can be missing, in which case a maximally uninformative prior is assumed (i.e. uniform distribution).
tol
tolerance threshold to determine convergence. See 'details' section.
lambda
Logical. Should λ -values be returned?
Author
Bill Shipley bill.shipley@usherbrooke.ca
\
Ported to FD by Etienne Laliberté.
Details
The biological model of community assembly through trait-based habitat filtering (Keddy 1992) has been translated mathematically via a maximum entropy (maxent) model by Shipley et al. (2006) and Shipley (2009). A maxent model contains three components: (i) a set of possible states and their attributes, (ii) a set of macroscopic empirical constraints, and (iii) a prior probability distribution q = [ q j ] .
In the context of community assembly, states are species, macroscopic empirical constraints are community-aggregated traits, and prior probabilities q are the relative abundances of species of the regional pool (Shipley et al. 2006, Shipley 2009). By default, these prior probabilities q are maximally uninformative (i.e. a uniform distribution), but can be specificied otherwise (Shipley 2009, Sonnier et al. 2009).
To facilitate the link between the biological model and the mathematical model, in the following description of the algorithm states are species and constraints are traits.
Note that if constr is a matrix or data frame containing several sets (rows), a maxent model is run on each individual set. In this case if prior is a vector, the same prior is used for each set. A different prior can also be specified for each set. In this case, the number of rows in prior must be equal to the number of rows in constr .
If q is not specified, set p j = 1 / S for each of the S species (i.e. a uniform distribution), where p j is the probability of species j , otherwise p j = q j .
Calulate a vector c = [ c i ] = { c 1 , c 2 , … , c T } , where c i = ∑ j = 1 S t i j ; i.e. each c i is the sum of the values of trait i over all species, and T is the number of traits.
Repeat for each iteration k until convergence:
For each trait t i (i.e. row of the constraint matrix) calculate:
γ i ( k ) = l n ( t ¯ i ∑ j = 1 S ( p j ( k ) t i j ) ) ( 1 c i )
This is simply the natural log of the known community-aggregated trait value to the calculated community-aggregated trait value at this step in the iteration, given the current values of the probabilities. The whole thing is divided by the sum of the known values of the trait over all species.
Calculate the normalization term Z :
Z ( k ) = ( ∑ j = 1 S p j ( k ) e ( ∑ i = 1 T γ i ( k ) t i j ) )
Calculate the new probabilities p j of each species at iteration k + 1 :
p j ( k + 1 ) = p j ( k ) e ( ∑ i = 1 T γ i ( k ) t i j ) Z ( k )
If | m a x ( p ( k + 1 ) − p ( k ) ) | ≤ tolerance threshold (i.e. argument tol ) then stop, else repeat steps 1 to 3.
When convergence is achieved then the resulting probabilities ( p ^ j ) are those that are as close as possible to q j while simultaneously maximize the entropy conditional on the community-aggregated traits. The solution to this problem is the Gibbs distribution:
p ^ j = q j e ( − ∑ i = 1 T λ i t i j ) ∑ j = 1 S q j e ( − ∑ i = 1 T λ i t i j ) = q j e ( − ∑ i = 1 T λ i t i j ) Z
This means that one can solve for the Langrange multipliers (i.e. weights on the traits, λ i ) by solving the linear system of equations:
( l n ( p ^ 1 ) l n ( p ^ 2 ) ⋮ l n ( p ^ S ) ) = ( λ 1 , λ 2 , … , λ T ) [ t 11 t 12 … t 1 S − l n ( Z ) t 21 t 22 ⋮ t 2 S − l n ( Z ) ⋮ ⋮ ⋮ ⋮ t T 1 t T 2 … t T S − l n ( Z ) ] − l n ( Z )
This system of linear equations has T + 1 unknowns (the T values of λ plus l n ( Z ) ) and S equations. So long as the number of traits is less than S − 1 , this system is soluble. In fact, the solution is the well-known least squares regression: simply regress the values l n ( p ^ j ) of each species on the trait values of each species in a multiple regression.
The intercept is the value of l n ( Z ) and the slopes are the values of λ i and these slopes (Lagrange multipliers) measure by how much the l n ( p ^ j ) , i.e. the l n (relative abundances), changes as the value of the trait changes. maxent.test provides permutation tests for maxent models (Shipley 2010).
References
Della Pietra, S., V. Della Pietra, and J. Lafferty (1997) Inducing features of random fields. IEEE Transactions Pattern Analysis and Machine Intelligence 19:1-13.
Keddy, P. A. (1992) Assembly and response rules: two goals for predictive community ecology. Journal of Vegetation Science 3:157-164.
Shipley, B., D. Vile, and É. Garnier (2006) From plant traits to plant communities: a statistical mechanistic approach to biodiversity. Science 314: 812--814.
Shipley, B. (2009) From Plant Traits to Vegetation Structure: Chance and Selection in the Assembly of Ecological Communities. Cambridge University Press, Cambridge, UK. 290 pages.
Shipley, B. (2010) Inferential permutation tests for maximum entropy models in ecology. Ecology in press.
Sonnier, G., Shipley, B., and M. L. Navas. 2009. Plant traits, species pools and the prediction of relative abundance in plant communities: a maximum entropy approach. Journal of Vegetation Science in press.
See Also
functcomp to compute community-aggregated traits, and maxent.test for the permutation tests proposed by Shipley (2010).
Another faster version of maxent for multicore processors called maxentMC is available from Etienne Laliberté ( etiennelaliberte@gmail.com). It's exactly the same as maxent but makes use of the multicore, doMC, and foreach packages. Because of this, maxentMC only works on POSIX-compliant OS's (essentially anything but Windows).
Examples
Run this code
Run the code above in your browser using DataLab