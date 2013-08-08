##
#  Copyright (c) 2011 LabKey Corporation
# 
#  Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
##
#options(echo=TRUE);
library(lattice);

labkey.data <- labkey.data[!is.na(labkey.data$date),];
labkey.data <- labkey.data[!is.na(labkey.data$result),];
labkey.data <- labkey.data[!is.na(labkey.data$population),];
labkey.data <- labkey.data[!is.na(labkey.data$subjectid),];



labkey.data$date = as.Date(labkey.data$date);

#str(labkey.data)

uniquePlots = paste(labkey.data$subjectid, labkey.data$population, sep=';');
size = length(unique(uniquePlots));

if (size > 0){

    png(filename="${imgout:graph.png}",
        width=800,
        height=(400 * size)
        );

    myPlot = xyplot(result ~ date | population * subjectid,
        data=labkey.data,
        #type="o",
        layout=c(1,size),
        xlab="Sample Date",
        ylab="Absolute Count (cells/uL)",
        auto.key = TRUE,
        #scales=list(x=list(relation="free", tick.number=10)),
        #par.settings = list(strip.background = list(col = c("light grey")) )
        );

    print(myPlot);

    dev.off();

} else {
    print("No subjects selected");
}

