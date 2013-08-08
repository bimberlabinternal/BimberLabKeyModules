/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
select
  cast(p.peptideId as varchar) as target,
  'Peptide' as type,
  max(p.sequence) as sequence

from laboratory.peptides p
group by p.peptideId

UNION ALL

SELECT
  pp.pool_name as target,
  'Pool' as type,
  null as sequence

FROM elispot_assay.peptide_pools pp

UNION ALL

SELECT 'Con A' as target, 'Control' as type, null as sequence

UNION ALL

SELECT 'No stim' as target, 'Control' as type, null as sequence