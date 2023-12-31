-- CREATE DATABASE IF NOT EXISTS naina_entity ;

CREATE TABLE people (
    id SERIAL PRIMARY KEY ,
    name VARCHAR(255) ,
    ok BOOLEAN ,
    dtn TIMESTAMP 
);

CREATE TABLE car (
    id SERIAL PRIMARY KEY ,
    imma VARCHAR(255) ,
    color VARCHAR(255) ,
    kilometrage NUMERIC(10)
);

CREATE TABLE people_car(
    id SERIAL PRIMARY KEY ,
    id_people INT REFERENCES people (id) ON DELETE CASCADE,
    id_car INT REFERENCES car (id) ON DELETE CASCADE ,
    date TIMESTAMP NOT NULL
);