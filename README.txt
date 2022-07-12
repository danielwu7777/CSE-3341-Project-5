name: Daniel Wu

Files: All Non-terminal class .java files
       Executor class: includes all helper methods

Comments: All semantic checking is implemented. I added some helper methods in Executor for garbage collection. 

Description: I created an array in the executor to keep track of the number of references for each position in the heap.
I incremented/decremented according to the project description.

Testing: Most of my errors were from null pointer exceptions and garbage collecting in the wrong places.
To debug where these errors were, I used print statements to show the current state of my reference counts array.