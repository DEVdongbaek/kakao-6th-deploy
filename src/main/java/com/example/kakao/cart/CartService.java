package com.example.kakao.cart;

import com.example.kakao._core.errors.exception.Exception400;
import com.example.kakao._core.errors.exception.Exception404;
import com.example.kakao.product.option.Option;
import com.example.kakao.product.option.OptionJPARepository;
import com.example.kakao.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class CartService {

    private final CartJPARepository cartJPARepository;
    private final OptionJPARepository optionJPARepository;

    @Transactional
    public void addCartList(List<CartRequest.SaveDTO> requestDTOs, User sessionUser) {
        // 1. 동일한 옵션이 들어오면 예외처리
        verifyDuplicateOptionId(requestDTOs);

        // 2. cartJPARepository.findByOptionIdAndUserId() 조회 -> 존재하면 장바구니에 수량을 추가하는 업데이트를 해야함. (더티체킹하기)
        for (CartRequest.SaveDTO requestDTO : requestDTOs) {
            Optional<Cart> cart = cartJPARepository.findByOptionIdAndUserId(requestDTO.getOptionId(), sessionUser.getId());

            if (cart.isPresent()) {
                cart.get().update(requestDTO.getOptionId(), requestDTO.getQuantity());
            }

            // 3. [2번이 아니라면] 유저의 장바구니에 담기
            int optionId = requestDTO.getOptionId();
            int quantity = requestDTO.getQuantity();
            Option optionPS = optionJPARepository.findById(optionId)
                    .orElseThrow(() -> new Exception404("해당 옵션을 찾을 수 없습니다 : " + optionId));
            int price = optionPS.getPrice() * quantity;
            Cart cartOP = Cart.builder().user(sessionUser).option(optionPS).quantity(quantity).price(price).build();
            cartJPARepository.save(cartOP);

        }
    }

    public CartResponse.FindAllDTO findAll(User user) {
        List<Cart> cartList = cartJPARepository.findByUserIdOrderByOptionIdAsc(user.getId());
        // Cart에 담긴 옵션이 3개이면, 2개는 바나나 상품, 1개는 딸기 상품이면 Product는 2개인 것이다.
        return new CartResponse.FindAllDTO(cartList);
    }

    public CartResponse.FindAllDTOv2 findAllv2(User user) {
        List<Cart> cartList = cartJPARepository.findByUserIdOrderByOptionIdAsc(user.getId());
        return new CartResponse.FindAllDTOv2(cartList);
    }

    @Transactional
    public CartResponse.UpdateDTO update(List<CartRequest.UpdateDTO> requestDTOs, User user) {
        List<Cart> cartList = cartJPARepository.findAllByUserId(user.getId());

        // 1. 유저 장바구니에 아무것도 없으면 예외처리
        if (cartList.isEmpty()) {
            throw new Exception404("장바구니에 아무것도 존재하지 않습니다.");
        }

        // 2. cartId:1, cartId:1 이렇게 requestDTOs에 동일한 장바구니 아이디가 두번 들어오면 예외처리
        verifyDuplicateCartId(requestDTOs);

        // 3. 유저 장바구니에 없는 cartId가 들어오면 예외처리
        List<Integer> cartIds = cartList.stream().map(cart -> cart.getId()).collect(Collectors.toList());

        for (CartRequest.UpdateDTO requestDTO : requestDTOs) {
            if (!cartIds.contains(requestDTO.getCartId())) {
                throw new Exception400("장바구니에 존재하지 않는 cartId 입니다. " + requestDTO.getCartId());
            }
        }

        // 위에 3개를 처리하지 않아도 프로그램은 잘돌아간다. 예를 들어 1번을 처리하지 않으면 for문을 돌지 않고, cartList가 빈배열 []로 정상응답이 나감.
        for (Cart cart : cartList) {
            for (CartRequest.UpdateDTO updateDTO : requestDTOs) {
                if (cart.getId() == updateDTO.getCartId()) {
                    cart.update(updateDTO.getQuantity(), cart.getOption().getPrice() * updateDTO.getQuantity());
                }
            }
        }

        return new CartResponse.UpdateDTO(cartList);
    } // 더티체킹


    private void verifyDuplicateOptionId(List<CartRequest.SaveDTO> requestDTOs){
        HashSet<Integer> optionIds = new HashSet<>();
        for (CartRequest.SaveDTO requestDTO : requestDTOs) {
            int optionId = requestDTO.getOptionId();
            if (!optionIds.add(optionId)) {
                // Duplicate optionId found
                throw new Exception400("동일한 옵션이 중복으로 들어옵니다 : " + optionId);
            }
        }
    }

    private void verifyDuplicateCartId(List<CartRequest.UpdateDTO> requestDTOs){
        HashSet<Integer> cartIds = new HashSet<>();
        for (CartRequest.UpdateDTO requestDTO : requestDTOs) {
            int cartId = requestDTO.getCartId();
            if (!cartIds.add(cartId)) {
                // Duplicate optionId found
                throw new Exception400("동일한 장바구니가 중복으로 들어옵니다 : " + cartId);
            }
        }
    }
}
