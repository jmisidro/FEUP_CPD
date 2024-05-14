# Distributed Systems Project - Pop Quiz

This project is a simple quiz application that allows users to participate in random trivia quizzes. The application is built using Java and uses a client-server architecture.

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

What things you need to install the software and how to install them.

- Java SE 21 or above
- JSON && BCRYPT libraries (included in the project under `/libs` directory)

### Installing

A step by step series of examples that tell you how to get a development environment running.

1. Clone the repository to your local machine.
2. Open the project in your favorite IDE or terminal.
3. Navigate to the project directory (`assign2/src/`) in your terminal.
4. Compile the files in the project directory:

```bash
javac -cp '.:libs/*' *.java
```

### Running the Server

Finally, run the server in a port of your choice and specify the database file to use.

```bash
java -cp '.:libs/*' Server <PORT> <DATABASE> [MODE]

# Example for the server running on port 8080,
# with the database file database.json and ranked mode
java -cp '.:libs/*' Server 8080 database.json 1
```
Note: The database file should be a JSON file containing the user information in the [required format](#database). Furthermore, the database file should be located in the server directory (`assign2/src/server/`).


### Running the Client

Run the client in a separate terminal window. The client should connect to the server running on the specified port:

```bash
java -cp '.:libs/*' Connection <PORT>
```


## Database

The database file should be a JSON file containing the user information in the following format:

```json
{
  "users": [
    {
      "username": "zemiguel",
      "password": "password",
      "token": "",
      "rank": 0
    },
    {
      "username": "fabiorocha",
      "password": "password",
      "token": "",
      "rank": 0
    },
    {
      "username": "zeguedes",
      "password": "password",
      "token": "",
      "rank": 0
    }
  ]
}
```

The password should be hashed using the BCRYPT algorithm. The token field is used to store the user's session token. The rank field is used to store the user's session token.

## Tokens

The server generates a token for each user when they login/register. This token is stored in the database as well as in the user's file in the `player/` directory. 

## Fault Tolerance

We employed a simple fault tolerance mechanism in the server. If the user disconnects from the server while in the waiting queue, the server keeps track of the player's position in the queue. If the user restores their connection within 15 seconds using their token, which is automatically saved in the `player/`directory, the server will place the user back in the queue at the same position.