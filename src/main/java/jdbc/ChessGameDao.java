package jdbc;

import domain.ChessGame;
import domain.chessboard.ChessBoard;
import domain.chessboard.Empty;
import domain.chessboard.EmptyType;
import domain.chessboard.Square;
import domain.chessboard.SquareStatus;
import domain.chessboard.Type;
import domain.coordinate.Position;
import domain.piece.Bishop;
import domain.piece.Color;
import domain.piece.InitPawn;
import domain.piece.King;
import domain.piece.Knight;
import domain.piece.Pawn;
import domain.piece.PieceType;
import domain.piece.Queen;
import domain.piece.Rook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ChessGameDao {

    private static final String SERVER = "localhost:13306";
    private static final String DATABASE = "chess";
    private static final String OPTION = "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";
    private static final String INVALID_DATA_ERROR_MESSAGE = "올바르지 않은 데이터입니다.";
    private static final int CHESS_BOARD_SIZE = 8;

    private static final Map<Type, Function<Color, SquareStatus>> squareStatusMapper = Map.of(
            EmptyType.EMPTY, color -> new Empty(),
            PieceType.KING, King::new,
            PieceType.PAWN, Pawn::new,
            PieceType.INIT_PAWN, InitPawn::new,
            PieceType.BISHOP, Bishop::new,
            PieceType.KNIGHT, Knight::new,
            PieceType.QUEEN, Queen::new,
            PieceType.ROOK, Rook::new
    ); // 이건 클래스를 따로 만들어서 분리해야할까요? ex) SquareStatusMapper

    public Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:mysql://" + SERVER + "/" + DATABASE + OPTION, USERNAME, PASSWORD);
        } catch (final SQLException exception) {
            throw new IllegalStateException(INVALID_DATA_ERROR_MESSAGE);
        }
    }

    public List<String> findAllId() {
        try (final Connection connection = getConnection()) {
            PreparedStatement findAll = connection.prepareStatement("SELECT id FROM chess_game");
            ResultSet findAllResult = findAll.executeQuery();
            return getAllId(findAllResult);
        } catch (SQLException exception) {
            throw new IllegalStateException(INVALID_DATA_ERROR_MESSAGE);
        }
    }

    private List<String> getAllId(ResultSet findAllResult) throws SQLException {
        List<String> allId = new ArrayList<>();

        while (findAllResult.next()) {
            allId.add(findAllResult.getString("id"));
        }

        return allId;
    }

    
    public String save(ChessGame chessGame) {
        try (final Connection connection = getConnection()) {
            String saveId = saveChessGame(connection, chessGame.getColorTurn());
            savePieces(chessGame.getChessBoard(), connection, saveId);
            return saveId;
        } catch (SQLException exception) {
            throw new IllegalStateException(INVALID_DATA_ERROR_MESSAGE);
        }
    }

    private String saveChessGame(Connection connection, Color color) throws SQLException {
        PreparedStatement chessGameSave = connection.prepareStatement(
                "INSERT INTO chess_game(turn) VALUES(?)", Statement.RETURN_GENERATED_KEYS
        );

        chessGameSave.setString(1, color.name());
        chessGameSave.executeUpdate();
        return getSaveId(chessGameSave);
    }

    private String getSaveId(PreparedStatement chessGameSave) throws SQLException {
        ResultSet generatedKeys = chessGameSave.getGeneratedKeys();
        String saveId = null;

        if (generatedKeys.next()) {
            saveId = generatedKeys.getString(1);
        }

        return saveId;
    }

    private void savePieces(ChessBoard chessBoard, Connection connection, String currentId) throws SQLException {
        PreparedStatement pieceSave = connection.prepareStatement(
                "INSERT INTO chess_board(x, y, piece_type, piece_color, game_id) VALUES(?, ?, ?, ?, ?)"
        );
        for (int x = 0; x < 8; x++) {
            saveColumnPieces(chessBoard, pieceSave, x, currentId);
        }
    }

    private void saveColumnPieces(ChessBoard chessBoard, PreparedStatement pieceSave, int x, String currentId) throws SQLException {
        for (int y = 0; y < 8; y++) {
            SquareStatus squareStatus = chessBoard.findSquare(Position.of(x, y)).getSquareStatus();
            pieceSave.setString(1, Integer.toString(x));
            pieceSave.setString(2, Integer.toString(y));
            pieceSave.setString(3, squareStatus.getType().name());
            pieceSave.setString(4, squareStatus.getColor().name());
            pieceSave.setString(5, currentId);
            pieceSave.executeUpdate();
        }
    }

    
    public ChessGame select(String id) {
        try (Connection connection = getConnection()){
            Color turn = getTurn(id, connection);
            ChessBoard chessBoard = getChessBoard(id, connection);
            return new ChessGame(turn, chessBoard);
        } catch (SQLException | IllegalStateException exception) {
            throw new IllegalStateException(INVALID_DATA_ERROR_MESSAGE);
        }
    }

    private Color getTurn(String id, Connection connection) throws SQLException {
        PreparedStatement selectChessGame = connection.prepareStatement("SELECT turn FROM chess_game where id = ?");
        selectChessGame.setString(1, id);
        ResultSet selectChessGameSet = selectChessGame.executeQuery();
        String turn = null;

        if (selectChessGameSet.next()) {
            turn = selectChessGameSet.getString("turn");
        }

        return Color.fromName(turn);
    }

    private ChessBoard getChessBoard(String id, Connection connection) throws SQLException {
        PreparedStatement selectChessBoard = connection.prepareStatement(
                "SELECT x, y, piece_type, piece_color FROM chess_board WHERE game_id = ?"
        );
        ChessBoard chessBoard = ChessBoard.generateEmptyBoard();
        selectChessBoard.setString(1, id);
        ResultSet selectChessBoardSet = selectChessBoard.executeQuery();
        return getChessBoardByQueryResult(chessBoard, selectChessBoardSet);
    }

    private ChessBoard getChessBoardByQueryResult(ChessBoard chessBoard, ResultSet selectChessBoardSet) throws SQLException {
        while (selectChessBoardSet.next()) {
            int x = Integer.parseInt(selectChessBoardSet.getString("x"));
            int y = Integer.parseInt(selectChessBoardSet.getString("y"));
            String pieceType = selectChessBoardSet.getString("piece_type");
            String pieceColor = selectChessBoardSet.getString("piece_color");
            Position position = Position.of(x, y);
            chessBoard.findSquare(position).bePiece(getSquare(pieceType, pieceColor));
        }

        return chessBoard;
    }

    private Square getSquare(String pieceType, String pieceColor) {
        Color color = Color.fromName(pieceColor);

        List<Type> types = new ArrayList<>(List.of(PieceType.values()));
        types.addAll(List.of(EmptyType.values()));

        Type resultType = types.stream()
                .filter(type -> pieceType.equals(type.name()))
                .findFirst()
                .orElseThrow(IllegalAccessError::new);
        return new Square(squareStatusMapper.get(resultType).apply(color));
    }

    
    public void update(String id, ChessGame chessGameAfterProcess) {
        ChessGame chessGameBySelect = select(id);

        try (Connection connection = getConnection()){
            updateChessGameTurn(id, chessGameAfterProcess, connection);
            updateChessBoard(id, chessGameAfterProcess, chessGameBySelect, connection);
        } catch (SQLException | IllegalStateException exception) {
            throw new IllegalStateException(INVALID_DATA_ERROR_MESSAGE);
        }
    }

    private void updateChessGameTurn(String id, ChessGame chessGameAfterProcess, Connection connection) throws SQLException {
        PreparedStatement chessGameTurnUpdate = connection.prepareStatement(
                "UPDATE chess_game SET turn = ? WHERE id = ?"
        );
        chessGameTurnUpdate.setString(1, chessGameAfterProcess.getColorTurn().name());
        chessGameTurnUpdate.setString(2, id);
        chessGameTurnUpdate.executeUpdate();
    }

    private void updateChessBoard(String id, ChessGame chessGameAfterProcess, ChessGame chessGameBySelect, Connection connection) throws SQLException {
        PreparedStatement chessBoardUpdate = connection.prepareStatement(
                "UPDATE chess_board SET piece_type = ?, piece_color = ? WHERE x = ? and y = ? and game_id = ?"
        );
        chessBoardUpdate.setString(5, id);

        for (int x = 0; x < CHESS_BOARD_SIZE; x++) {
            updateEachColumnInRow(chessBoardUpdate, x, chessGameAfterProcess, chessGameBySelect);
        }
    }

    private void updateEachColumnInRow(PreparedStatement chessBoardUpdate, int x, ChessGame chessGameAfterProcess, ChessGame chessGameBySelect) throws SQLException {
        for (int y = 0; y < CHESS_BOARD_SIZE; y++) {
            Position findPosition = Position.of(x, y);
            Square square1 = new Square(chessGameBySelect.getChessBoard()
                    .findSquare(findPosition)
                    .getSquareStatus());
            Square square2 = new Square(chessGameAfterProcess.getChessBoard()
                    .findSquare(findPosition)
                    .getSquareStatus());
            updateIfNotSameSquare(chessBoardUpdate, x, y, square1, square2);
        }
    }

    private void updateIfNotSameSquare(PreparedStatement chessBoardUpdate, int x, int y, Square square1, Square square2) throws SQLException {
        if (isNotSameSquare(square1, square2)) {
            chessBoardUpdate.setString(1, square2.getType().name());
            chessBoardUpdate.setString(2, square2.getColor().name());
            chessBoardUpdate.setString(3, Integer.toString(x));
            chessBoardUpdate.setString(4, Integer.toString(y));
            chessBoardUpdate.executeUpdate();
        }
    }

    private boolean isNotSameSquare(Square insertionSquareStatus, Square squareStatus) {
        return isNotSameColor(insertionSquareStatus, squareStatus)
                || isNotSameType(insertionSquareStatus, squareStatus);
    }

    private boolean isNotSameType(Square insertionSquareStatus, Square squareStatus) {
        return insertionSquareStatus.isNotSameType(squareStatus.getType());
    }

    private boolean isNotSameColor(Square insertionSquareStatus, Square squareStatus) {
        return insertionSquareStatus.isNotSameColor(squareStatus.getColor());
    }

    
    public void delete(String id) {
        try (Connection connection = getConnection()) {
            PreparedStatement deleteChessGame = connection.prepareStatement("DELETE FROM chess_game WHERE id = ?");
            deleteChessGame.setString(1, id);
            deleteChessGame.executeUpdate();
        } catch (SQLException | IllegalStateException exception) {
            throw new IllegalStateException(INVALID_DATA_ERROR_MESSAGE);
        }
    }

}
