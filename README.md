# ITAC Command-Line Interface

This is the repository for the Gemini Observatory ITAC application.

## Installing Itac

### 1. Overview

Installing ITAC is straightforward but you may require a bit of assistance if you're not comfortable using the command line. Anyone from ITS or the software group can help you if you get stuck.

Open a command shell (Terminal.app on the Mac) and continue with Step 2.

### 2. Install Coursier

First install the Coursier native launcher. This will let you set up ITAC as well as the Java Virtual Machine, if necessary. Follow the instructions [here](https://get-coursier.io/docs/cli-overview.html#install-native-launcher). On success you will be able to do:

```
$ cs --help
Coursier 2.0.0-RC6-10
Usage: cs [options] [command] [command-options]

Available commands: bootstrap, complete, fetch, install, java, java-home, launch, publish, resolve, setup, uninstall, update

Type  cs command --help  for help on an individual command
```

### 3. Install Java if Needed

See if Java is already on your path. If it is, `java -version` should show you something like this:

```
$ java -version
java version "1.8.0_144"
Java(TM) SE Runtime Environment (build 1.8.0_144-b01)
Java HotSpot(TM) 64-Bit Server VM (build 25.144-b01, mixed mode)
```

If you see an error message or version earlier than 1.8.x then continue on below, otherwise skip to Step 3.

Ok so you need to install Java. Do the following.

```
$ cs java --env
...
export CS_FORMER_JAVA_HOME="$JAVA_HOME"
export JAVA_HOME="/root/.cache/coursier/jvm/adopt@1.8.0-242"
export PATH="/root/.cache/coursier/jvm/adopt@1.8.0-242/bin:$PATH"
```

At the end of the output will be some `export` statements. Add these to your startup profile (usually `~/.bash_profile`) to add the JVM to your path.

Close and re-open your terminal window and `java -version` should now work.

### 4. Install ITAC

Use `cs` to install ITAC.

```
cs install --channel edu.gemini:itac-channel itac
...
Wrote itac
Warning: /root/.local/share/coursier/bin is not in your PATH
To fix that, add the following line to your shell configuration file

export PATH="$PATH:/root/.local/share/coursier/bin"
```

If the output ends in the warning above, add the `export` statement to your startup profile (usually `~/.bash_profile`) to add Coursier-managed applications to your path.

Close and re-open your terminal window and `itac --help` should now run and print a help message.

### 5. Update ITAC

Once ITAC is installed you can update to the latest version thus:

```
cs update itac
```

And uninstall:

```
cs uninstall itac
```

## Using ITAC

ITAC is a command-line application that reads proposal XML files along with some configuration information stored in YAML files, and produces an observing queue. The main workflow is:

1. Initialize a workspace directory to contain all your input files, and copy proposal XML files into the `proposals` directory.
1. Customize configuration files as necessary to specify partner times, shutdown blocks, target changes, and so on.
1. Create queues and examine the output, tweaking configuration until you're satisfied.
1. Send emails and ingest the proposals into the ODB.

Keep in mind the following big ideas:

- All the input files are just normal text files. You can rename them, make backups, store them in source control, manage them however you like.
- All proposal "edits" are specified as part of configuration and occur as proposals are loaded from disk. *The XML files are never touched.* This means you undo or change edits simply by updating the `edits.yaml` file (see below).
- The input files completely specify the Queue. If you want to have several queues that you can compare, you can have several sets of input files.

Now let's examine these steps in more detail.

### Initialize and Configure an ITAC Workspace

> **Note:** this step requires a connection to the internal Gemini network, so if you're working off-site you need to have the VPN turned on.

Create a new directory, move into it, and initialize the ITAC workspace for next semester.

```
$ mkdir itac-example
$ cd itac-example
$ itac init 2020B
[INFO ] Creating folder: email_templates
[INFO ] Creating folder: proposals
[INFO ] Writing: ./email_templates/ngo_classical.vm
[INFO ] Writing: ./email_templates/ngo_exchange.vm
[INFO ] Writing: ./email_templates/ngo_joint_classical.vm
[INFO ] Writing: ./email_templates/ngo_joint_exchange.vm
[INFO ] Writing: ./email_templates/ngo_joint_poor_weather.vm
[INFO ] Writing: ./email_templates/ngo_joint_queue.vm
[INFO ] Writing: ./email_templates/ngo_poor_weather.vm
[INFO ] Writing: ./email_templates/ngo_queue.vm
[INFO ] Writing: ./email_templates/pi_successful.vm
[INFO ] Writing: ./email_templates/unsuccessful.vm
[INFO ] Writing: ./common.yaml
[INFO ] Writing: ./edits.yaml
[INFO ] Writing: ./gn-queue.yaml
[INFO ] Writing: ./gs-queue.yaml
[INFO ] Fetching current rollover report from Gemini North...
[INFO ] Got rollover information for 2020A
[INFO ] Writing: ./gn-rollovers.yaml
[INFO ] Fetching current rollover report from Gemini South...
[INFO ] Got rollover information for 2020A
[INFO ] Writing: ./gs-rollovers.yaml
[INFO ] init: initialized ITAC workspace in .
```

`itac` has created a bunch of files. Let's look at them.

```
$ tree
.
├── common.yaml
├── edits.yaml
├── email_templates
│   ├── ngo_classical.vm
│   ├── ngo_exchange.vm
│   ├── ngo_joint_classical.vm
│   ├── ngo_joint_exchange.vm
│   ├── ngo_joint_poor_weather.vm
│   ├── ngo_joint_queue.vm
│   ├── ngo_poor_weather.vm
│   ├── ngo_queue.vm
│   ├── pi_successful.vm
│   └── unsuccessful.vm
├── gn-queue.yaml
├── gn-rollovers.yaml
├── gs-queue.yaml
├── gs-rollovers.yaml
└── proposals
```

- `common.yaml` contains configuration that applies to all queues and is unlikely to change much. You will need to edit this file to specify shutdown times and partner contact emails. The rest is probably ok.
- `edits.yaml` contains edits to proposals that you will make in the process of constructing your queue. We will revisit this later.
- `email_templates/` contains the default email templates which are used to generate emails that are sent to partners and PIs. You can change these if you wish (and if you do, let us know so we can change the defaults) but they're probably ok.
- `gn-queue.yaml` and `gs-queue.yaml` specify site-specific queue configurations. The most important part here is `hours` which specifies per-partner hours in bands 1, 2, and 3. Remaining configuration is probably ok.
- `gn-rollovers.yaml` and `gn-rollovers.yaml` contain rollover reports fetched from the ODBs at GN and GS. You may wish to adjust the times here. To re-fetch a rollover report you can say `itac --force rollover --south` (or `--north`). Note that you must have an internal or VPN connection to do this.
- `proposals/` is where your proposal XML files (from Jared's system) need to go.

### Construct a Queue

Once your workspace is set up you can run a queue, but if you want to play with this software it's likely you don't have any proposals for the current semester. So to do this we can **turn back time** and run a previous semester's queue again. Let's use 2020A.

1. Put the 2020A proposal XMLs in `proposals/`.
1. Edit `common.yaml` and change the semester to `2020A`.
1. Replace the rollover files if desired (Rob can send you 20A rollovers).

You should now be able to run a queue and see output that includes rejection messages, bin usage, and band assignments.

```
itac queue --south
```

### Editing Proposals

TBD

### Finalizing a Queue

TBD