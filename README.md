## Text Editor
### Design

+------------------+      +------------------+      +--------------------+      
|   Text, cursor   | ---> | Operations:      | ---> |  Underlying Data   |      
|                  |      | Insert, Delete,  |      |  Piece Table,      |
|                  | ---> | Modify           | ---> |  Rope              |
+------------------+      +------------------+      +--------------------+

Text & Cursor: Represents user input or interactions with the editor, including typing, navigation, and selection.
Operations: Encapsulates commands such as Insert, Delete, and Modify.
Underlying Data Structure: Maintains the actual text efficiently using a Piece Table or Rope.

### Key Classes and Responsibilities
#### Document
The Document interface, acting as a bridge between the editor operations and the underlying data structure.

Key Responsibilities
    Row and Column Management:
        Tracks row-based text layout and supports cursor-based operations.
    Character Encoding:
        Handles text encoding (e.g., UTF-8).
    Row Index:
        Maintains row boundaries for efficient row-based text access.

#### TextEdit
Manages high-level text operations and undo/redo functionality.

Key Responsibilities
    Insert:
        Adds text at the cursor position.
    Delete:
        Removes characters or ranges.
    Modify:
        Replaces text within a specified range.
    Undo/Redo:
        Supports reverting or reapplying edits.
    Edit Queue:
        Temporarily holds unflushed edits for in-memory operations.
    Undo/Redo Stacks:
        Supports reversing or reapplying edits.
    Buffer:
        Simulates changes in memory before committing them to the document.



### Underlying data structure (Pieces table, rope)
#### Piece Table
The Piece Table is a data structure that tracks edits to text by referencing regions of memory (original file buffer and appended buffer) rather than copying or modifying the text directly.

Structure

Piece List: An ordered collection of Piece objects. Each Piece stores:
Buffer Target: Reference to the original buffer (read-only) or the appended buffer (editable).
Buffer Index: Start offset in the buffer.
Length: Number of characters represented by the piece.

1. Insert
Inserts new text at a specified position in the editor.

Steps:
    Create a new Piece referencing the appended buffer.
    Add the new text to the appended buffer.
    Update the piece list:
        If inserting at the boundary of an existing piece, add the new piece directly.
        If inserting within a piece, split the piece and insert the new piece in between.
        Update indices to reflect the changes.

1. Delete

Removes a specified range of text.

Steps:
    Identify the range of pieces affected by the deletion.
    Remove the pieces entirely within the range.
    Split pieces partially covered by the range:
        Keep the unaffected portion(s).
        Update indices and reduce the total length.

1. Modify (Replace)

Combines deletion and insertion operations to replace a range of text.

Steps:
    Perform the deletion.
    Insert the new text at the same position.

#### Example
Scenario: Inserting "ipsum" into the text "Lorem um" at position 6.

Initial State
    readBuffer: "Lorem um"
    appendBuffer: ""

Piece Table:
    Operation: Insert "ipsum" at position 6
    Append "ipsum" to appendBuffer.
    Split Piece(1) at position 6.
    Insert a new piece referencing appendBuffer.

Resulting State
    readBuffer: "Lorem um"
    appendBuffer: "ipsum"

Piece Table:
    Piece(0): "Lorem "
    Piece(1): "ipsum"
    Piece(2): " um"
    Rendered Text: "Lorem ipsum um"

#### Rope


### Resources
 - https://github.com/veler/Csharp-Piece-Table-Implementation
 - https://code.visualstudio.com/blogs/2018/03/23/text-buffer-reimplementation#
 - https://en.wikipedia.org/wiki/Piece_table
 - https://github.com/component/rope/tree/master
 - https://www.geeksforgeeks.org/ropes-data-structure-fast-string-concatenation/

## Code highlighting

### Syntax Analysis
+------------------+      +------------------+      +--------------------+      +------------------+
|   Source Code    | ---> |   Parser         | ---> |  Syntax Tree       | ---> |  Apply styles    |
|                  |      |                  |      |  (Hierarchical AST)|      |                  |  
+------------------+      +------------------+      +--------------------+      +------------------+
Input                     Core Engine                   Output

### Key steps:
1. Parser reads the source code.
2. It generates a concrete syntax tree (CST) or abstract syntax tree (AST) based on language grammar.
3. The syntax tree can be queried, navigated, or used for syntax highlighting, error detection, etc.

### Data Structures

1. Interval Tree
    The Interval Tree organizes and stores token highlighting intervals for efficient querying and updating.
    Purpose:
        Enables efficient operations like retrieving tokens within a given range.

2. HighlightInterval
    A data structure representing the start, end, and type of a token.
    Attributes:
        start (inclusive): Start position of the interval.
        end (exclusive): End position of the interval.
        type: The token type (e.g., KEYWORD, COMMENT).

3. BracketInfo
    Tracks nested brackets for multi-line syntax highlighting.
        Attributes:
            char: Bracket character (e.g., (, {).
            row, col: Position of the bracket.
            nestingColorIndex: Indicates the color level (0, 1, or 2).

### Resources
- https://www.geeksforgeeks.org/introduction-to-syntax-analysis-in-compiler-design/
- https://stackoverflow.com/a/62685296
- https://github.com/zanshin/interpreter/blob/main/lexer/lexer_test.go
- https://github.com/amazon-science/incremental-parsing
- https://en.wikipedia.org/wiki/Interval_tree
- https://www.geeksforgeeks.org/check-for-balanced-parentheses-in-an-expression/
- https://www.baeldung.com/cs/tree-segment-interval-range-binary-indexed
- https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html#thread-confinement-fine-grained
- https://kotlinlang.org/docs/channels.html#prime-numbers-with-pipeline
- https://zyedidia.github.io/notes/yedidia_thesis.pdf

## UI Components
### Design
+--------------------+      +--------------------+      +-----------------------+
|  MainFrame         | ---> |  KTextPane         | ---> | TextPaneContent       |
|  JFrame-based GUI  |      |  JPanel with text  |      |  Manages text data,   |
|                    | ---> |  rendering, input  | ---> |  caret operations,    |
|                    |      |  handling          |      |  and editing          |
+--------------------+      +--------------------+      +-----------------------+

MainFrame: Acts as the main application window, providing buttons, layout management, and user interaction handling.
KTextPane: Custom text editing component for displaying and interacting with text.
TextPaneContent: Back-end interface managing text data, highlighting, and synchronization with editing operations.

#### MainFrame
MainFrame is the top-level container for the editor. It initializes and manages UI components like KTextPane, buttons for file operations, and the cursor position label.

Key Responsibilities:
    File Operations:
        Open and read files asynchronously.
        Save text content to files.
    Layout Management:
        Arranges KTextPane, buttons, and other components in a structured layout.
    Cursor Display:
        Tracks and displays cursor position in a status label.
        
#### KTextPane
Custom implementation extending JPanel. Handles text rendering, caret movement, selection, and basic editing.

Key Responsibilities:
    Rendering:
        Displays text row by row.
        Highlights line numbers, text, and selection regions.
    Caret & Selection:
        Manages caret position and movement.
        Handles text selection for copy, cut, and paste operations.
    Event Handling:
        Processes keyboard inputs for navigation and editing.
        Captures mouse clicks and drags for selection.
    Custom Painting:
        Uses Graphics2D to draw text and UI elements.
    
#### TextPaneContent
The backend text manager for KTextPane, responsible for interacting with the underlying text model and applying changes.

Key Responsibilities:
    Text Management:
        Provides row-based access to text data.
        Handles insertions, deletions, and replacements.
    Highlighting:
        Supports syntax or text highlighting through re-rendering.
    Synchronization:
        Ensures consistency between the visible text pane and the underlying text model.

### Resources
 - https://mockk.io/
 - https://discuss.kotlinlang.org/t/how-would-i-use-a-button-in-kotlin-using-swing/6730/2