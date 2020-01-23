# Installing RabbitMQ on Windows

> This is what we've found to be successful.

##  Software install

Open a command prompt as Administrator

- Update scoop bucket list:

    scoop bucket add extras

- Install the software 

    scoop install rabbitmq --global

> those are 2 dashes in front of global

## Cookie issue resolution

- Launch Windows Explorer
- Navigate to `c:\windows\system32`
- Double click on config folder to open
- Press continue button prompt to gain access (if needed)
- Double click on `systemProfile` folder to open
- Press continue button prompt to gain access (if needed)
- Right-click on the `.erlang.cookie` file and select Copy from the menu
- Navigate to the `c:\users\<your user name>` folder
- Right click on that folder and select Paste from the menu
- Replace the existing `.erlang.cookie` file.

##  Quick test

    rabbitmqctl status 

This should return information about the rabbit service, and NOT show any errors.

## Management console installation

    rabbitmq-plugins enable rabbitmq_management
    
> Note dash after first mq, underscore after second

Close command prompt

##  Management console quick test

- Launch a browser
- Enter `localhost:15672` as the address
- At the logon page, use `guest/guest` for the user name/password
- The management console page should appear

## Additional notes

The service install sets the startup mode to automatic, so RabbitMQ service should start at system start up
You can start/stop the service using the `rabbitmq-service` batch file or the standard Windows Service Control Manager.

You will need to set up vhosts, exchanges, and queues using the management console depending on the needs of the project you are working with.

