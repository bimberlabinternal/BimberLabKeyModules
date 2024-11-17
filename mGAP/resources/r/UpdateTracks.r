library(Rlabkey)
library(dplyr)

# This script is designed to be run externally per release, to identify subject that need to be added to the releaseTrackSubsets table:

testByCenter <- function(centerName, trackName) {
  dat <- suppressWarnings(labkey.selectRows(
    baseUrl="https://prime-seq.ohsu.edu", 
    folderPath="/Internal/ColonyData", 
    schemaName="mgap", 
    queryName="sampleSummary", 
    viewName="", 
    colSelect="subjectId,externalAlias", 
    colFilter=makeFilter(
      c("tracks", "DOES_NOT_CONTAIN", trackName),
      c("center", "EQUAL", centerName)), 
    containerFilter=NULL, 
    colNameOpt="rname"
  ))
  
  print(paste0(trackName, ': ', nrow(dat)))
  
  if (nrow(dat) == 0) {
    return(NULL)
  }
  
  return(data.frame(trackName = trackName, subjectId = dat$subjectid))
}

testBySpecies <- function(speciesList, trackName) {
  dat <- suppressWarnings(labkey.selectRows(
    baseUrl="https://prime-seq.ohsu.edu", 
    folderPath="/Internal/ColonyData", 
    schemaName="mgap", 
    queryName="sampleSummary", 
    viewName="", 
    colSelect="subjectId,externalAlias", 
    colFilter=makeFilter(
      c("tracks", "DOES_NOT_CONTAIN", trackName),
      c("species", "IN", paste0(speciesList, collapse = ';'))), 
    containerFilter=NULL, 
    colNameOpt="rname"
  ))
  
  print(paste0(trackName, ': ', nrow(dat)))
  
  if (nrow(dat) == 0) {
    return(NULL)
  }
  
  return(data.frame(trackName = trackName, subjectId = dat$subjectid))
}

toInsert <- rbind(
  testByCenter('CNPRC', 'CNPRC Animals'),
  testByCenter('TNPRC', 'TNPRC Animals'),
  testByCenter('ENPRC', 'ENPRC Animals'),
  testByCenter('NEPRC', 'NEPRC Animals'),
  testByCenter('SNPRC', 'SNPRC Animals'),
  testByCenter('ONPRC', 'ONPRC Animals'),
  testByCenter('MDA', 'MDA Animals'),
  testByCenter('WFU', 'WFU Animals'),
  testByCenter('CPRC', 'CPRC Animals'),
  testBySpecies(c('RHESUS MACAQUE', 'Rhesus', 'Macaca mulatta'), 'Rhesus Macaques'),
  testBySpecies(c('JAPANESE MACAQUE', 'Macaca fuscata'), 'Japanese Macaques')
)


if (FALSE) {
  added <- labkey.insertRows(
    baseUrl="https://prime-seq.ohsu.edu", 
    folderPath="/Internal/ColonyData", 
    schemaName="mgap", 
    queryName="releaseTrackSubsets", 
    toInsert = toInsert
  )
}


# Now ensure all tracks exist:
existingTracks <- labkey.selectRows(
  baseUrl="https://prime-seq.ohsu.edu", 
  folderPath="/Internal/ColonyData", 
  schemaName="mgap", 
  queryName="releaseTracks",
  colNameOpt="rname"
)

missingTrackNames <- labkey.selectRows(
  baseUrl="https://prime-seq.ohsu.edu", 
  folderPath="/Internal/ColonyData", 
  schemaName="mgap", 
  queryName="releaseTrackSubsets",
  colSelect="trackName",
  colNameOpt="rname"
) %>% 
  filter(!trackname %in% existingTracks$trackname) %>%
  select(trackname) %>% unique()

if (nrow(missingTrackNames) > 0) {
  toAdd <- data.frame(trackName = missingTrackNames$trackname, label = missingTrackNames$trackname, isprimarytrack = FALSE)
  toAdd$Category <- 'Species Dataset'
  # Add anything else desired, like species, source, url, description, category
  
  if (FALSE) {
    added <- labkey.insertRows(
      baseUrl="https://prime-seq.ohsu.edu", 
      folderPath="/Internal/ColonyData", 
      schemaName="mgap", 
      queryName="releaseTracks", 
      toInsert = toAdd
    )
  }
}

