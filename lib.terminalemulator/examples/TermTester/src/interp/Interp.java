/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package interp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author ivan
 */
public final class Interp {
    private static final Interp singleton = new Interp();

    private final HashMap<String, Cmd> commands = new HashMap<String, Cmd>();

    private boolean done;
    private ListenerOutput output;
    private Cmd currentCommand;

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    private Interp() {
        new BaseCmdSet(this);
        new SysCmdSet(this);
    }

    public static Interp getDefault() {
	return singleton;
    }

    public void addCmd(Cmd cmd) {
	cmd.setInterp(this);
        for (String name : cmd.names())
            commands.put(name, cmd);
    }

    public void setDone() {
        done = true;
    }

    void setCurrentCommand(Cmd currentCommand) {
        this.currentCommand = currentCommand;
    }

    Cmd getCurrentCommand() {
        return currentCommand;
    }

    boolean isDone() {
        return done;
    }

    void setOutput(ListenerOutput output) {
        this.output = output;
    }

    public void print(String msg) {
        output.print(msg);
    }

    public void println(String msg) {
        output.println(msg);
    }

    public void printf(String fmt, Object ... args) {
        output.printf(fmt, args);
    }

    public void error(String fmt, Object... args) {
        if (getCurrentCommand() != null) {
            output.printf("%s: ", getCurrentCommand().name());
        }
        output.printf(fmt, args);
        output.printf("\n");
        if (getCurrentCommand() != null) {
            getCurrentCommand().help();
        }
        throw new RuntimeException();
    }

    void greet() {
	output.printf("Hello.\n");
	output.printf("Working directory: %s\n", System.getProperty("user.dir"));
    }

    Collection<Cmd> commands() {
        return commands.values();
    }

    Cmd lookup(String name) {
        return commands.get(name);
    }

    void source(String file, boolean quiet) {
	Reader r;
	BufferedReader br;
	try {
	    r = new FileReader(file);
	    br = new BufferedReader(r);
	} catch(Exception x) {
	    if (!quiet)
		output.printf("Couldn't open \"%s\"\n", file);
	    return;
	}

	String line;
	try {
	    while ((line = br.readLine()) != null)
		execute(output, line);
	} catch (IOException x) {
	}
	
    }


    private enum State {
        NONE,
        INIT,
        WORD,
        SPACE,
        QUOTE
    }

    private enum CType {
        SPACE,
        WORD,
        EOL,
        QUOTE,
    }
    private static CType classify(char c) {
        if (c == '"')
            return CType.QUOTE;
        else if (Character.isSpaceChar(c))
            return CType.SPACE;
        else
            return CType.WORD;
    }

    private List<String> tokenize(String line) {
        List<String> words = new LinkedList<String>();
        String word = "";
        State state = State.INIT;
        CType ctype = CType.SPACE;
        // for (int cx = 0; cx <= line.length(); cx++) {
        for (int cx = 0; ctype != CType.EOL; cx++) {
            char c = 0;
            if (cx == line.length()) {
                ctype = CType.EOL;
            } else if (state != State.QUOTE && line.charAt(cx) == '#') {
                ctype = CType.EOL;
            } else {
                c = line.charAt(cx);
                ctype = classify(c);
            }
            switch (state) {
                case NONE:
                    error("bad state NONE");
                    break;
                case INIT:
                case SPACE:
                    switch (ctype) {
                        case EOL:
                        case SPACE:
                            break;
                        case WORD:
                            word += c;
                            state = State.WORD;
                            break;
                        case QUOTE:
                            state = State.QUOTE;
                            break;
                    }
                    break;
                case WORD:
                    switch (ctype) {
                        case EOL:
                        case SPACE:
                            words.add(word);
                            word = "";
                            state = State.SPACE;
                            break;
                        case WORD:
                            word += c;
                            break;
                        case QUOTE:
                            state = State.QUOTE;
                            break;
                    }
                    break;
                case QUOTE:
                    switch (ctype) {
                        case QUOTE:
                            // End quote
                            state = State.WORD;
                            break;
                        case EOL:
                            // EOL within a quote. SHOULD issue a warning
                            words.add(word);
                            word = "";
                            state = State.SPACE;
                            break;
                        case SPACE:
                        case WORD:
                            word += c;
                            break;
                    }
                    break;
            }
        }
        return words;
    }

    void execute(ListenerOutput out, String line) {
        //
        // tokenize it
        //
        List<String> words = tokenize(line);
        /*
        System.out.printf("Words are:\n");
        for (String w : words)
            System.out.printf("\t'%s'\n", w);
        */

        //
        // dispatch it
        //
        if (words.size() > 0) {
            String cmdName = words.get(0);
            // OLD Command command = Command.lookup(cmdName);
            Cmd command = commands.get(cmdName);
            setCurrentCommand(command);
            try {
                if (command == null) {
                    error("Unrecognized command \'%s\'", words.get(0));

                } else {
                    // skip the first word for args
                    String[] args = new String[words.size()-1];
                    for (int wx = 1; wx < words.size(); wx++)
                        args[wx-1] = words.get(wx);

                    command.run(args);
                }
            } catch (RuntimeException x) {
                StackTraceElement[] stackTrace = x.getStackTrace();
                printf("Exception\n%s\n", x.toString());
                for (int sx = 0; sx < stackTrace.length; sx++)
                    printf("\t%s\n", stackTrace[sx]);
            }
        }
    }
}
