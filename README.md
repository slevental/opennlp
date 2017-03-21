# OpenNLP
Mirror of Apache OpenNLP (Incubating)

# Fork 
Some optimizations made in parser/tagger's models to make it work faster. GISModel class implemented using open-address hash tables which could be very slow in case of high density of occupied values, espesially in case if ~80% requests are FPs.
