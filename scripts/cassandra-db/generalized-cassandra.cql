-- The keyspace names are variablized in this file. Replace them before execution

CREATE KEYSPACE IF NOT EXISTS {{ env }}_hierarchy_store WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': '1'
};

CREATE KEYSPACE IF NOT EXISTS {{ env }}_question_store WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': '1'
};

CREATE TABLE IF NOT EXISTS {{ env }}_hierarchy_store.questionset_hierarchy (
  identifier text,
  hierarchy text,
  instructions text,
  outcomeDeclaration text,
  PRIMARY KEY (identifier)
);

CREATE TABLE IF NOT EXISTS {{ env }}_question_store.question_data (
  identifier text,
  body blob,
  editorState text,
  answer blob,
  solutions text,
  instructions text,
  hints text,
  media text,
  responseDeclaration text,
  interactions text,
  outcomeDeclaration text,
  feedback text,
  PRIMARY KEY (identifier)
);
