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

CREATE SCHEMA flowassays;
GO

CREATE TABLE flowassays.populations (
    Name varchar(200) NOT NULL,
    Marker varchar(200),
    Comments text default null,

    CreatedBy USERID,
    Created datetime,
    ModifiedBy USERID,
    Modified datetime,

    CONSTRAINT PK_populations PRIMARY KEY (name)
);

INSERT INTO flowassays.populations (name) VALUES ('NK');
INSERT INTO flowassays.populations (name) VALUES ('CD14 Mono');
INSERT INTO flowassays.populations (name) VALUES ('T-Cells');
INSERT INTO flowassays.populations (name) VALUES ('B-Cells');
INSERT INTO flowassays.populations (name) VALUES ('CD4');
INSERT INTO flowassays.populations (name) VALUES ('CD14');
INSERT INTO flowassays.populations (name) VALUES ('CD8');


CREATE TABLE flowassays.units
(
    Unit varchar(100) NOT NULL,

    CONSTRAINT PK_units PRIMARY KEY (unit)
);

INSERT INTO flowassays.units VALUES ('cells/uL');
INSERT INTO flowassays.units VALUES ('Tc/uL');

UPDATE flowassays.populations SET Name = 'CD4 T-cells' WHERE Name = 'CD4';
UPDATE flowassays.populations SET Name = 'CD8 T-cells' WHERE Name = 'CD8';

INSERT INTO flowassays.units VALUES ('%');

CREATE TABLE flowassays.instruments
(
    Instrument varchar(200) NOT NULL,

    CONSTRAINT PK_instruments PRIMARY KEY (Instrument)
);

INSERT INTO flowassays.instruments VALUES ('BD LSR II');

CREATE TABLE flowassays.assay_types (
  rowid int identity(1,1),
  name varchar(100),
  description varchar(1000),

  constraint pk_assay_types PRIMARY KEY (rowid)
);


DELETE FROM flowassays.populations WHERE name = 'NK';
DELETE FROM flowassays.populations WHERE name = 'CD4';
DELETE FROM flowassays.populations WHERE name = 'CD8';
DELETE FROM flowassays.populations WHERE name = 'CD14';

DELETE FROM flowassays.populations where name = 'CD4 T-cells';
DELETE FROM flowassays.populations where name = 'CD8 T-cells';
INSERT INTO flowassays.populations (name) VALUES ('CD4 T-cells');
INSERT INTO flowassays.populations (name) VALUES ('CD8 T-cells');


INSERT INTO flowassays.populations (name) VALUES ('NK Cells');
INSERT INTO flowassays.populations (name) VALUES ('CD8+ NK Cells');
INSERT INTO flowassays.populations (name) VALUES ('NKG2A+ NK Cells');
INSERT INTO flowassays.populations (name) VALUES ('CD11c-DC');
INSERT INTO flowassays.populations (name) VALUES ('CD11c+ lo DC');
INSERT INTO flowassays.populations (name) VALUES ('CD11c+ MDC');
INSERT INTO flowassays.populations (name) VALUES ('CD8aCD4 T-cells');
INSERT INTO flowassays.populations (name) VALUES ('CD8a T-cells');

DELETE FROM flowassays.populations WHERE name = 'T-cells';
INSERT INTO flowassays.populations (name) VALUES ('T-cells');

INSERT INTO flowassays.assay_types(name) VALUES ('TruCount');